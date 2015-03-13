package shivanhunter.voxelgame;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.VertexAttribute;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;

public class VoxelModel {
	/*
	 * voxel data - bytes here are either 0 (no voxel) or an index into the
	 * color array (where a value of 3 is colors[2], and so on).
	 */
	private byte[][][] blocks;
	
	/*
	 * An array of no more than 255 colors for voxel indices
	 */
	private ArrayList<Color> colors;
	
	/*
	 * Cubic size of the voxel data
	 * (this may be split into width/height/depth later since flat models could
	 *  be storing many empty blocks)
	 */
	private int size;
	
	/*
	 * Scale of the model. Has no relevance to this modeller program, but
	 * affects how the model is drawn in a game world.
	 */
	private int scale;
	
	/*
	 * The model's root location. If a VoxelModel is drawn at (1, 1, 1), the
	 * rootLocation is the exact point that will be drawn at (1, 1, 1).
	 */
	private Vector3 rootLocation;
	
	// basic diffuse material for rendering voxels
	private Material mat;
	
	// the model representing the voxel data
	private Model model;

	/* 
	 * Constants: number of verts and indices in a quad, number of floats
	 * in a vertex
	 */
    private static final int VERTS = 4, INDS = 6, FLOATS = 9;
    
    /*
     * Data in a vertex: 3 position floats, 3 color floats, 3 normal floats
     */
    public static final VertexAttributes attributes = new VertexAttributes(
    		new VertexAttribute(VertexAttributes.Usage.Position, 3, "a_position"),
    		new VertexAttribute(VertexAttributes.Usage.ColorUnpacked, 3, "a_color"),
    		new VertexAttribute(VertexAttributes.Usage.Normal, 3, "a_normal")
    		);
	
	/*
	 * Axis is used to select an orthogonal direction in 3d space
	 */
	public enum Axis {
		POS_X,
		NEG_X,
		POS_Y,
		NEG_Y,
		POS_Z,
		NEG_Z
	}
	
	/**
	 * Constructs a VoxelModel using byte data from a file. Refer to
	 * voxel_spec.txt. Throws an IllegalArgumentException if the buffer's size
	 * does not match what is expected from the model's size and number of colors.
	 * 
	 * @param data the byte data from which to construct a model
	 */
	public VoxelModel(byte[] data) {
		ByteBuffer buffer = ByteBuffer.wrap(data);
		byte version, num_materials;
		
		try {
			// first four bytes give basic info
			version = buffer.get(); // version unused since only version 0 exists
			num_materials = buffer.get();
			size = buffer.get()+1;
			scale = buffer.get()+1;
		} catch (BufferUnderflowException e) {
			throw new IllegalArgumentException();
		}
		
		// verify size
		if (buffer.capacity() != 16 + num_materials*12 + size*size*size) {
			throw new IllegalArgumentException();
		}
		
		// set up objects/lists
		mat = new Material(ColorAttribute.createDiffuse(1f, 1f, 1f, 1));
		colors = new ArrayList<Color>();
		blocks = new byte[size][size][size];
		
		// next three floats are the root location
		rootLocation = new Vector3(
				buffer.getFloat(),
				buffer.getFloat(),
				buffer.getFloat());
		
		// next n*3 floats are the block colors
		for (int i = 0; i < num_materials; ++i) {
			colors.add(new Color(
					buffer.getFloat(),
					buffer.getFloat(),
					buffer.getFloat(),
					1));
		}
		
		// next n^3 bytes are the indices (block data)
		for (int i = 0; i < size; ++i) {
			for (int j = 0; j < size; ++j) {
				for (int k = 0; k < size; ++k) {
					blocks[i][j][k] = buffer.get();
				}
			}
		}

		// create model from loaded data
		updateMesh();
	}
	
	/**
	 * Deallocate LibGDX objects not handled by GC. Needs to be called on a
	 * VoxelModel before it is GC'd to prevent memory leak.
	 */
	public void dispose() {
		model.dispose();
	}
	
	/**
	 * Updates the mesh representing the voxel data. Should be called whenever
	 * the size, root location or any blockdata is changed.
	 */
	private void updateMesh() {
        // temp list of vertices
        ArrayList<Float> verticesList = new ArrayList<Float>();
    	float r, g, b;
        
        for (int i = 0; i < size; ++i) {
            for (int j = 0; j < size; ++j) {
                for (int k = 0; k < size; ++k) {
                	
                	// only create quad facing outwards if there's a block at this cell
                	if (blocks[i][j][k] > 0) {
                		
                		// set color for any of this block's quads
                		r = colors.get(blocks[i][j][k]-1).r;
                		g = colors.get(blocks[i][j][k]-1).g;
                		b = colors.get(blocks[i][j][k]-1).b;
                		
                		// only add quads if the block they're facing towards is empty
                		if (i == 0 || blocks[i-1][j][k] == 0) {
                			appendQuad(
                					verticesList,
                					i,   j,   k,
                					i,   j,   k+1,
                					i,   j+1, k+1,
                					i,   j+1, k,
                					r,   g,   b,  
                					Axis.NEG_X);
                		}
                		if (i == size-1 || blocks[i+1][j][k] == 0) {
                			appendQuad(
                					verticesList,
                					i+1, j,   k,
                					i+1, j+1, k,
                					i+1, j+1, k+1,
                					i+1, j,   k+1,
                					r,   g,   b,
                					Axis.POS_X);
                		}
                		if (j == 0 || blocks[i][j-1][k] == 0) {
                			appendQuad(
                					verticesList,
                					i,   j,   k,
                					i+1, j,   k,
                					i+1, j,   k+1,
                					i,   j,   k+1,
                					r,   g,   b,
                					Axis.NEG_Y);
                		}
                		if (j == size-1 || blocks[i][j+1][k] == 0) {
                			appendQuad(
                					verticesList,
                					i,   j+1, k,
                					i,   j+1, k+1,
                					i+1, j+1, k+1,
                					i+1, j+1, k,
                					r,   g,   b,
                					Axis.POS_Y);
                		}
                		if (k == 0 || blocks[i][j][k-1] == 0) {
                			appendQuad(
                					verticesList,
                					i,   j,   k,
                					i,   j+1, k,
                					i+1, j+1, k,
                					i+1, j,   k,
                					r,   g,   b,
                					Axis.NEG_Z);
                		}
                		if (k == size-1 || blocks[i][j][k+1] == 0) {
                			appendQuad(
                					verticesList,
                					i,   j,   k+1,
                					i+1, j,   k+1,
                					i+1, j+1, k+1,
                					i,   j+1, k+1,
                					r,   g,   b,
                					Axis.POS_Z);
                		}
                	}
                }
            }
        }
        
        int numQuads = verticesList.size()/FLOATS/VERTS;

        // create a mesh with room for the generated polygons
        Mesh mesh = new Mesh(true, numQuads*VERTS, numQuads*INDS, attributes);
        
        // create indices array
        short[] indices = new short[numQuads*INDS];
        
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
        
        // convert list of verts to float[]
        float[] vertices = new float[numQuads*FLOATS*VERTS];
        
        // a list of Float can't be converted directly to a float[] because java sucks
        // so iterate through
        for (int i = 0; i < verticesList.size(); ++i) {
        	vertices[i] = verticesList.get(i);
        }

        // put generated lists in mesh
		mesh.setVertices(vertices);
		mesh.setIndices(indices);
		
		// build a LibGDX Model using mesh and material
		ModelBuilder builder = new ModelBuilder();
		builder.begin();
		builder.part("", mesh, GL20.GL_TRIANGLES, mat);
		model = builder.end();
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
	private float getAmbientOcclusion(int x, int y, int z, Axis axis) {
		return getAmbientOcclusion(x, y, z, axis, AO_Quality);
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
	private float getAmbientOcclusion(int x, int y, int z, Axis axis, int radius) {
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
					if (x+i >= 0 && x+i < size &&
							y+j >= 0 && y+j < size &&
							z+k >= 0 && z+k < size &&
							blocks[x+i][y+j][z+k] > 0) cells++;
				}
			}
		}
		
		// number of opaque cells out of the maximum
		float proportion = (float)(cells/((Math.pow(radius*2, 3)/2)));
		
		// light amount is the inverse of this proportion
		float ao =  1-proportion;
		
		// recurse to get better results in tight corners
		return ao * (getAmbientOcclusion(x, y, z, axis, radius-1)+.1f)/1.1f;
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
	public void appendQuad(
			ArrayList<Float> vertexList, 
			float x1, float y1, float z1,
			float x2, float y2, float z2,
			float x3, float y3, float z3,
			float x4, float y4, float z4,
			float r, float g, float b,
			Axis axis) {
		
		// calculate ambient occlusion for each vertex
		float ambientOcclusion1 = getAmbientOcclusion((int)x1, (int)y1, (int)z1, axis);
		float ambientOcclusion2 = getAmbientOcclusion((int)x2, (int)y2, (int)z2, axis);
		float ambientOcclusion3 = getAmbientOcclusion((int)x3, (int)y3, (int)z3, axis);
		float ambientOcclusion4 = getAmbientOcclusion((int)x4, (int)y4, (int)z4, axis);
		
		// scale the verts up/down based on scale factor and move them to rootLocation
		x1 = (x1 + rootLocation.x) * scale/128f;
		y1 = (y1 + rootLocation.y) * scale/128f;
		z1 = (z1 + rootLocation.z) * scale/128f;
		
		x2 = (x2 + rootLocation.x) * scale/128f;
		y2 = (y2 + rootLocation.y) * scale/128f;
		z2 = (z2 + rootLocation.z) * scale/128f;
		
		x3 = (x3 + rootLocation.x) * scale/128f;
		y3 = (y3 + rootLocation.y) * scale/128f;
		z3 = (z3 + rootLocation.z) * scale/128f;
		
		x4 = (x4 + rootLocation.x) * scale/128f;
		y4 = (y4 + rootLocation.y) * scale/128f;
		z4 = (z4 + rootLocation.z) * scale/128f;
		
		// flip quad if necessary because of ambient occlusion
		// see "details regarding meshing":
		// http://0fps.net/2013/07/03/ambient-occlusion-for-minecraft-like-worlds/
		boolean flipped = (
				ambientOcclusion1 + ambientOcclusion3 < 
				ambientOcclusion2 + ambientOcclusion4);
		
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
	 * Returns the Model for this VoxelModel.
	 * @return the Model for this VoxelModel
	 */
	public Model getModel() {
		return this.model;
	}
	
	/**
	 * Returns the size of the model.
	 * @return the size of the model
	 */
	public int getSize() {
		return size;
	}
	
	/**
	 * Returns the root lcoation of the model.
	 * @return the root lcoation of the model
	 */
	public Vector3 getRootLocation() {
		return rootLocation;
	}
}
