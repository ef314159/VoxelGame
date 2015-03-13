package shivanhunter.voxelgame;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

/**
 * Represents a small playable area of the game world. Contains block data for
 * the area, as well as a mesh based on the data and a list of ModelInstances
 * to display decoration (grass, flowers etc). 
 */
public class Region implements Comparable<Region> {
	// region size consts
	// do not have to be powers of 2, but WIDTH must be larger than AO_Quality
	public static final int WIDTH = 32;
	public static final int HEIGHT = 256;
	
	// raw block data used for collision and mesh creation
	// is a byte[WIDTH][HEIGHT][WIDTH] returned from regionGenerator
	private byte[][][] data;
	
	// region coordinates (not block coordinates) for this region
	private final int regionX, regionZ;
	
	// seed for generating this Region (should be the same across all Regions)
	private final long seed;
	
	// importance of this Region (used by Map)
	public float generationPriority;
	
	/* 
	 * polygon data for this Region's model. Created in a worker thread. Since
	 * LibGDX Models cannot be instantiated in worker threads (as they require
	 * an OpenGL context), this data must be passed to the main thread so the
	 * Region can finish creating its mesh.
	 */
	private float[] vertices = null;
	private short[] indices = null;
	
	/* 
	 * Model(s) for decoration layers. Models are loaded early and are stored
	 * statically - any Region can create its own ModelInstance of any of these
	 * Models.
	 */
	private static VoxelModel flowers;
	
	// rendering stuff
	// simple diffuse material used across all Regions
	private Material mat;
	
	// Model representing the Region's block data
	private Model model;
	// instance of the Model to render
	private ModelInstance instance;
	
	// collection of instances of deco models to render as a deco layer
	private Collection<ModelInstance> decoLayer = new LinkedList<ModelInstance>();
	
	/**
	 * Creates and generates a new Region at the given coordinates, with the
	 * given seed and priority.
	 * 
	 * Coordinates are region coordinates, not block coordinates. Region
	 * coordinates can be converted from unit coordinates by dividing by WIDTH.
	 * 
	 * generationPriority is used by the Map class when generating regions.
	 * 
	 * @param regionX the region's X coordinate
	 * @param regionZ the region's Z coordinate
	 * @param seed the seed to use for generation
	 * @param generationPriority the region's importance (distance from the player)
	 */
	public Region(int regionX, int regionZ, long seed, float generationPriority) {
		this.regionX = regionX;
		this.regionZ = regionZ;
		this.seed = seed;
		this.generationPriority = generationPriority;
		data = new RegionGenerator().generate(regionX*WIDTH, regionZ*WIDTH, seed);
		
		mat = new Material(ColorAttribute.createDiffuse(1f, 1f, 1f, 1));
	}
	
	/**
	 * Loads the deco models (currently a single model) for Regions to use.
	 * Should be called before any Regions are created.
	 * Can throw an IllegalArgumentException if any models fail to load.
	 * TODO: gracefully handle exception
	 */
	public static void loadDecoModels() {
		FileHandle file = Gdx.files.internal("deco/flowers.voxel");
		flowers = new VoxelModel(file.readBytes());
	}
	
	/**
	 * Renders this Region's Model.
	 * 
	 * @param environment the LinGDX Environment for rendering
	 * @param batch the LibGDX ModelBatch for rendering
	 * @param drawDeco whether to draw the deco layer's ModelInstances
	 */
	public void render(Environment environment, ModelBatch batch, boolean drawDeco) {
		if (instance == null) return;
		
		batch.render(instance, environment);
		
		if (drawDeco) {
			for (ModelInstance i : decoLayer) {
				batch.render(i, environment);
			}
		}
	}
	
	/**
	 * Loads the Mesh from the vertex and index arrays. Should be called after
	 * these arrays are populated in a worker thread.
	 */
	public void loadMesh() {
		Mesh mesh = new Mesh(true, vertices.length, indices.length, new VertexAttributes(
        		new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
        		new VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 3, "a_color"),
        		new VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal")
        		));

		mesh.setVertices(vertices);
		mesh.setIndices(indices);
		
		vertices = null;
		indices = null;
		
		// build a LibGDX Model using mesh and material
		ModelBuilder builder = new ModelBuilder();
		builder.begin();
		builder.part("", mesh, GL20.GL_TRIANGLES, mat);
		model = builder.end();
		instance = new ModelInstance(model);
		instance.transform.translate(regionX*WIDTH, 0, regionZ*WIDTH);

        for (int i = 0; i < WIDTH; ++i) {
            for (int j = 0; j < HEIGHT; ++j) {
                for (int k = 0; k < WIDTH; ++k) {
                	if (data[i][j][k] == 2) {
                		ModelInstance instance = new ModelInstance(flowers.getModel());
                		
                		instance.transform.translate(
                				regionX*WIDTH + i + 0.5f,
                				j,
                				regionZ*WIDTH + k + 0.5f);
                		
                		instance.transform.rotate(0, 1, 0, (int)(Noise.get(i, j+1, k, seed)*3f)*90);
                		decoLayer.add(instance);
                	}
                }
            }
        }
	}

	/* 
	 * Constants: number of verts and indices in a quad, number of floats
	 * in a vertex
	 */
    private static final int VERTS = 4, INDS = 6, FLOATS = 9;
	
	/**
	 * Populates the vertex and index arrays for this Region based on the
	 * existing block data. Requires knowledge of adjacent Regions (this
	 * Region's Neighborhood) to create polygons and calculate ambient
	 * occlusion. This method assumes the given Neighborhood is complete (that
	 * n.isComplete() returns true).
	 * 
	 * @param n this Region's Neighborhood (assumed to be complete)
	 */
	public void createMesh(Neighborhood n) {
        ArrayList<Float> verticesList = new ArrayList<Float>();
        
        float r, g, b, c;
        
        for (int i = 0; i < WIDTH; ++i) {
            for (int j = 0; j < HEIGHT; ++j) {
                for (int k = 0; k < WIDTH; ++k) {
                	
                	// only create quad facing outwards if there's a block at this cell
                	if (isVisible(data[i][j][k])) {

                		// set color for any of this block's quads
                    	r = 0.25f;
                    	g = 0.85f;
                    	b = 0.0f;
                    	
                    	c = MathUtils.random()*.02f;
                    	r += c; 
                    	g += c;
                    	g += c;

                		// only add quads if the block they're facing towards is empty
                		if (!isOpaque(n.get(i-1, j, k))) {
                			appendQuad(n, verticesList,
                					i,   j,   k,
                					i,   j,   k+1,
                					i,   j+1, k+1,
                					i,   j+1, k,
                					r, g, b,
                					VoxelModel.Axis.NEG_X);
                		}
                		if (!isOpaque(n.get(i+1, j, k))) {
                			appendQuad(n, verticesList,
                					i+1, j,   k,
                					i+1, j+1, k,
                					i+1, j+1, k+1,
                					i+1, j,   k+1,
                					r, g, b,
                					VoxelModel.Axis.POS_X);
                		}
                		if (j > 0 && !isOpaque(data[i][j-1][k])) {
                			appendQuad(n, verticesList,
                					i,   j,   k,
                					i+1, j,   k,
                					i+1, j,   k+1,
                					i,   j,   k+1,
                					r, g, b,
                					VoxelModel.Axis.NEG_Y);
                		}
                		if (j == HEIGHT-1 || !isOpaque(data[i][j+1][k])) {
                			appendQuad(n, verticesList,
                					i,   j+1, k,
                					i,   j+1, k+1,
                					i+1, j+1, k+1,
                					i+1, j+1, k,
                					r, g, b,
                					VoxelModel.Axis.POS_Y);
                		}
                		if (!isOpaque(n.get(i, j, k-1))) {
                			appendQuad(n, verticesList,
                					i,   j,   k,
                					i,   j+1, k,
                					i+1, j+1, k,
                					i+1, j,   k,
                					r, g, b,
                					VoxelModel.Axis.NEG_Z);
                		}
                		if (!isOpaque(n.get(i, j, k+1))) {
                			appendQuad(n, verticesList,
                					i,   j,   k+1,
                					i+1, j,   k+1,
                					i+1, j+1, k+1,
                					i,   j+1, k+1,
                					r, g, b,
                					VoxelModel.Axis.POS_Z);
                		}
                	}
                }
            }
        }
        
        int numQuads = verticesList.size()/FLOATS/VERTS;
        
        vertices = new float[numQuads*FLOATS*VERTS];
        indices = new short[numQuads*INDS];

        // each polygon is 6 indices for each 4 vertices: two triangles
        // for each quad
        for (int i = 0; i < numQuads; ++i) {
        	indices[i*INDS + 0] = (short)(i*VERTS + 0);
        	indices[i*INDS + 1] = (short)(i*VERTS + 1);
        	indices[i*INDS + 2] = (short)(i*VERTS + 2);
        	
        	indices[i*INDS + 3] = (short)(i*VERTS + 2);
        	indices[i*INDS + 4] = (short)(i*VERTS + 3);
        	indices[i*INDS + 5] = (short)(i*VERTS + 0);
        }
        
        for (int i = 0; i < verticesList.size(); ++i) {
        	vertices[i] = verticesList.get(i);
        }
	}
	
	/*
	 * AMBIENT OCCLUSION:
	 * 
	 * AO in a voxel model works by darkening cartain vertices based on the
	 * arrangement of the vaces around them. Refer to
	 *  
	 * http://0fps.net/2013/07/03/ambient-occlusion-for-minecraft-like-worlds/
	 * 
	 * for details.
	 * 
	 * This algorithm uses the proportion of opaque cells around a vertex to
	 * generate a darkening factor which is multiplied to the vertex color.
	 * This proportion is based on the cells in a cubic area around the vertex,
	 * but only in a given direction - if the face normal points toward the
	 * negative X axis, only cells with x coordinates less than the vertex's
	 * coordinate will be counted. The total area of cells checked for
	 * opaqueness is radius^3 / 2.
	 * 
	 * Since large radii lead to less detail around sharp edges, the algorithm
	 * works recursively, further darkening the cells by using lower radii as
	 * well. Therefore, a radius of 2 will lead to a total of
	 * (4*4*4/2) + (2*2*2/2) = 40 cells being checked.
	 */
	
	// The radius to use for ambient occlusion
	private static final int AO_Quality = 3;

	/**
	 * Gets a value from 0 to 1 representing ambient occlusion for the vertex
	 * at the given lcoation, using the given normal axis. The returned value
	 * if to be multiplied by the vertex color: a value of 1 means no darkening.
	 * This method calls the recursive method using AO_Quality as a radius.
	 * 
	 * @param x the vertex location in x
	 * @param y the vertex location in y
	 * @param z the vertex location in z
	 * @param axis the normal axis
	 * @return the lightness of the AO at the given point
	 */
	private static float getAmbientOcclusion(int x, int y, int z, VoxelModel.Axis axis, Neighborhood n) {
		return getAmbientOcclusion(x, y, z, axis, n, AO_Quality);
	}

	/**
	 * Recursive method used to get AO within a given radius.
	 * 
	 * @param x the vertex location in x
	 * @param y the vertex location in y
	 * @param z the vertex location in z
	 * @param axis the normal axis
	 * @param radius the radius of blocks to check for opaqueness
	 * @return the lightness of the AO at the given point
	 */
	private static float getAmbientOcclusion(int x, int y, int z, VoxelModel.Axis axis,
			Neighborhood n, int radius) {
		
		// base case - no darkening
		if (radius < 1) return 1;

		// count of opaque cells
		int cells = 0;

		// start and end values based on radius
		int startX = -radius,
				endX = radius,
				startY = -radius,
				endY = radius,
				startZ = -radius,
				endZ = radius;

		// cut one of them short based on which direction is being checked
		switch (axis) {
			case NEG_X: endX = 0; break;
			case NEG_Y: endY = 0; break;
			case NEG_Z: endZ = 0; break;
			case POS_X: startX = 0; break;
			case POS_Y: startY = 0; break;
			case POS_Z: startZ = 0; break;
		}

		// count up opaque cells
		for (int i = startX; i < endX; ++i) {
			for (int j = startY; j < endY; ++j) {
				for (int k = startZ; k < endZ; ++k) {
					if (isOpaque(n.get(x+i, y+j, z+k))) cells++;
				}
			}
		}

		// number of opaque cells out of the maximum
		float proportion = (float)(cells/((Math.pow(radius*2, 3)/2)));

		// light amount is the inverse of this proportion
		float ao =  MathUtils.clamp(1 - proportion*1, 0, 1);
		
		// recurse to get better results in tight corners
		return ao * (getAmbientOcclusion(x, y, z, axis, n, radius-1)+.1f)/1.1f;
	}

	/**
	 * Deallocate LibGDX objects not handled by GC. Needs to be called on a
	 * Region before it is GC'd to prevent memory leak.
	 */
	public void dispose() {
		if (model != null) model.dispose();
	}
	
	/**
	 * Gets the block data at the given coordinates. Throws an
	 * ArrayIndexOutOfBoundsException if the given indices are invlaid. Inputs
	 * are not block coordinates, they are indices into the Region's data
	 * array (e. g. x must be between 0 and Region.WIDTH-1).
	 * 
	 * @param x the x index of the data to return
	 * @param y the y index of the data to return
	 * @param z the z index of the data to return
	 * @return the data at the given indices
	 */
	public byte get(int x, int y, int z) {
		return data[x][y][z];
	}
	
	/**
	 * Checks whather a given block ID is opaque.
	 * 
	 * @param datum the block to check
	 * @return whether the block is opaque
	 */
	public static boolean isOpaque(byte datum) {
		return datum == 1;
	}
	
	/**
	 * Checks whether a given block should be drawn.
	 * 
	 * @param datum the block to check
	 * @return whether the block is visible
	 */
	public static boolean isVisible(byte datum) {
		return datum == 1;
	}
	
	/**
	 * Checks whether a given location collides with entities. As with get(),
	 * parameters are indices, not block coordinates in world space. As with
	 * get(), throws an ArrayIndexOutOfBoundsException if indices are not valid.
	 * 
	 * @param x the x index to check for collision
	 * @param y the y index to check for collision
	 * @param z the z index to check for collision
	 * @return whether the block at the given coordinates collides with entities
	 */
	public boolean collide(int x, int y, int z) {
		return data[x][y][z] == 1;
	}
	
	/**
	 * Gets the Region's x coordinate. This is in region coordinates, not block
	 * coordinates.
	 * 
	 * @return the Region's x coordinate
	 */
	public int getX() {
		return regionX;
	}

	/**
	 * Gets the Region's z coordinate. This is in region coordinates, not block
	 * coordinates.
	 * 
	 * @return the Region's z coordinate
	 */
	public int getZ() {
		return regionZ;
	}
	
	/**
	 * Converts block coordinates into region coordinates. Returns a MapCoord
	 * containing coordinated for the region encompassing the given block
	 * coordinates.
	 * 
	 * @param x the block coordinate in x
	 * @param z the block coordinate in z
	 * @return the MapCoord for the Region containing the block coordinate (x, z)
	 */
	public static MapCoord getRegionAt(float x, float z) {
		return new MapCoord(MathUtils.floor(x/WIDTH), MathUtils.floor(z/WIDTH), -1);
	}

	/**
	 * Adds a quad to the VertexList.
	 * 
	 * @param vertexList the list under construction
	 * @param x1 the x coordinate of the first vertex in counterclockwise order
	 * @param y1 the y coordinate of the first vertex in counterclockwise order
	 * @param z1 the z coordinate of the first vertex in counterclockwise order
	 * @param x2 the x coordinate of the second vertex in counterclockwise order
	 * @param y2 the y coordinate of the second vertex in counterclockwise order
	 * @param z2 the z coordinate of the second vertex in counterclockwise order
	 * @param x3 the x coordinate of the third vertex in counterclockwise order
	 * @param y3 the y coordinate of the third vertex in counterclockwise order
	 * @param z3 the z coordinate of the third vertex in counterclockwise order
	 * @param x4 the x coordinate of the fourth vertex in counterclockwise order
	 * @param y4 the y coordinate of the fourth vertex in counterclockwise order
	 * @param z4 the z coordinate of the fourth vertex in counterclockwise order
	 * @param r the red channel of the vertex color
	 * @param g the green channel of the vertex color
	 * @param b the blue channel of the vertex color
	 * @param axis the axis of the quad normal
	 */
	public static void appendQuad(Neighborhood n,
			ArrayList<Float> vertexList, 
			float x1, float y1, float z1,
			float x2, float y2, float z2,
			float x3, float y3, float z3,
			float x4, float y4, float z4,
			float r, float g, float b,
			VoxelModel.Axis axis) {

		// calculate ambient occlusion for each vertex
		float ambientOcclusion1 = getAmbientOcclusion((int)x1, (int)y1, (int)z1, axis, n);
		float ambientOcclusion2 = getAmbientOcclusion((int)x2, (int)y2, (int)z2, axis, n);
		float ambientOcclusion3 = getAmbientOcclusion((int)x3, (int)y3, (int)z3, axis, n);
		float ambientOcclusion4 = getAmbientOcclusion((int)x4, (int)y4, (int)z4, axis, n);

		// flip quad if necessary because of ambient occlusion
		// see "details regarding meshing":
		// http://0fps.net/2013/07/03/ambient-occlusion-for-minecraft-like-worlds/
		boolean flipped = (ambientOcclusion1 + ambientOcclusion3 < ambientOcclusion2 + ambientOcclusion4);

		// convert normal axis enum to xyz vector
		// vector components will be 0 except for the axis along which the normal points
		float nx = 0, ny = 0, nz = 0;
		switch(axis) {
			case NEG_X: nx = -1; break;
			case NEG_Y: ny = -1; break;
			case NEG_Z: nz = -1; break;
			case POS_X: nx = 1; break;
			case POS_Y: ny = 1; break;
			case POS_Z: nz = 1; break;
		}

		// add the first vertex first if quad is not flipped
		if (!flipped) {
			vertexList.add(x1);
			vertexList.add(y1);
			vertexList.add(z1);
			vertexList.add(r*ambientOcclusion1);
			vertexList.add(g*ambientOcclusion1);
			vertexList.add(b*ambientOcclusion1);
			vertexList.add(nx);
			vertexList.add(ny);
			vertexList.add(nz);
		}
		
		vertexList.add(x2);
		vertexList.add(y2);
		vertexList.add(z2);
		vertexList.add(r*ambientOcclusion2);
		vertexList.add(g*ambientOcclusion2);
		vertexList.add(b*ambientOcclusion2);
		vertexList.add(nx);
		vertexList.add(ny);
		vertexList.add(nz);
		
		vertexList.add(x3);
		vertexList.add(y3);
		vertexList.add(z3);
		vertexList.add(r*ambientOcclusion3);
		vertexList.add(g*ambientOcclusion3);
		vertexList.add(b*ambientOcclusion3);
		vertexList.add(nx);
		vertexList.add(ny);
		vertexList.add(nz);
		
		vertexList.add(x4);
		vertexList.add(y4);
		vertexList.add(z4);
		vertexList.add(r*ambientOcclusion4);
		vertexList.add(g*ambientOcclusion4);
		vertexList.add(b*ambientOcclusion4);
		vertexList.add(nx);
		vertexList.add(ny);
		vertexList.add(nz);

		// add first vertex last if quad is flipped
		if (flipped) {
			vertexList.add(x1);
			vertexList.add(y1);
			vertexList.add(z1);
			vertexList.add(r*ambientOcclusion1);
			vertexList.add(g*ambientOcclusion1);
			vertexList.add(b*ambientOcclusion1);
			vertexList.add(nx);
			vertexList.add(ny);
			vertexList.add(nz);
		}
	}
	
	/**
	 * Changes this Region's generationPriority for the Map. The PriorityQueue
	 * in the Map should be reordered after this function is called.
	 * 
	 * @param newPriority the new generation priority
	 */
	public void setGenerationPriority(float newPriority) {
		this.generationPriority = newPriority;
	}

	/**
	 * Compares this Region's generationPriority to another. Used in the Map's
	 * PriorityQueue.
	 */
	@Override public int compareTo(Region o) {
		return (int)Math.signum(generationPriority - o.generationPriority);
	}
}
