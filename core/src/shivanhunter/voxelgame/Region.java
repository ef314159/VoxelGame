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

public class Region implements Comparable<Region> {
	public static final int WIDTH = 32;
	public static final int HEIGHT = 256;
	private byte[][][] data;
	
	private final int regionX, regionZ;
	private final long seed;
	public float generationPriority;
	
	private float[] vertices = null;
	private short[] indices = null;
	
	private static VoxelModel flowers;
	
	private Model model;
	private ModelInstance instance;
	private Collection<ModelInstance> decoLayer = new LinkedList<ModelInstance>();
	private Material mat;
	
	public Region(int regionX, int regionZ, long seed, float generationPriority) {
		this.regionX = regionX;
		this.regionZ = regionZ;
		this.seed = seed;
		this.generationPriority = generationPriority;
		data = new RegionGenerator().generate(regionX*WIDTH, regionZ*WIDTH, seed);
		
		mat = new Material(ColorAttribute.createDiffuse(1f, 1f, 1f, 1));
	}
	
	public void render(Environment environment, ModelBatch batch, boolean drawDeco) {
		batch.render(instance, environment);
		
		if (drawDeco) {
			for (ModelInstance i : decoLayer) {
				batch.render(i, environment);
			}
		}
	}
	
	public static void loadDecoModels() {
		FileHandle file = Gdx.files.internal("deco/flowers.voxel");
		flowers = new VoxelModel(file.readBytes());
	}
	
	public void createMesh(Neighborhood n) {
		create(0, n);
	}
	
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
	
	private void create(int LOD, Neighborhood n) {
		//int LODSize = (int)Math.pow(2, LOD);
		
        final int VERTS = 4, INDS = 6, FLOATS = 9;
        
        int numQuads = 0;
        
        ArrayList<Float> verticesList = new ArrayList<Float>();
        
        for (int i = 0; i < WIDTH; ++i) {
            for (int j = 0; j < HEIGHT; ++j) {
                for (int k = 0; k < WIDTH; ++k) {
                	
                	float r = 0.25f, g = 0.85f, b = 0.0f;
                	float c = MathUtils.random()*.01f;
                	
                	r += c; g += c; g += c;
                	
                	if (isVisible(data[i][j][k])) {
                		if (!isOpaque(n.get(i-1, j, k))) {
                			appendQuad(n, verticesList,
                					i,   j,   k,
                					i,   j,   k+1,
                					i,   j+1, k+1,
                					i,   j+1, k,
                					r, g, b,
                					Axis.NEG_X);
                			numQuads++;
                		}
                		if (!isOpaque(n.get(i+1, j, k))) {
                			appendQuad(n, verticesList,
                					i+1, j,   k,
                					i+1, j+1, k,
                					i+1, j+1, k+1,
                					i+1, j,   k+1,
                					r, g, b,
                					Axis.POS_X);
                			numQuads++;
                		}
                		if (j > 0 && !isOpaque(data[i][j-1][k])) {
                			appendQuad(n, verticesList,
                					i,   j,   k,
                					i+1, j,   k,
                					i+1, j,   k+1,
                					i,   j,   k+1,
                					r, g, b,
                					Axis.NEG_Y);
                			numQuads++;
                		}
                		if (j == HEIGHT-1 || !isOpaque(data[i][j+1][k])) {
                			appendQuad(n, verticesList,
                					i,   j+1, k,
                					i,   j+1, k+1,
                					i+1, j+1, k+1,
                					i+1, j+1, k,
                					r, g, b,
                					Axis.POS_Y);
                			numQuads++;
                		}
                		if (!isOpaque(n.get(i, j, k-1))) {
                			appendQuad(n, verticesList,
                					i,   j,   k,
                					i,   j+1, k,
                					i+1, j+1, k,
                					i+1, j,   k,
                					r, g, b,
                					Axis.NEG_Z);
                			numQuads++;
                		}
                		if (!isOpaque(n.get(i, j, k+1))) {
                			appendQuad(n, verticesList,
                					i,   j,   k+1,
                					i+1, j,   k+1,
                					i+1, j+1, k+1,
                					i,   j+1, k+1,
                					r, g, b,
                					Axis.POS_Z);
                			numQuads++;
                		}
                	}
                }
            }
        }
        indices = new short[numQuads*INDS];
        
        for (int i = 0; i < numQuads; ++i) {
        	indices[i*INDS + 0] = (short)(i*VERTS + 0);
        	indices[i*INDS + 1] = (short)(i*VERTS + 1);
        	indices[i*INDS + 2] = (short)(i*VERTS + 2);
        	
        	indices[i*INDS + 3] = (short)(i*VERTS + 2);
        	indices[i*INDS + 4] = (short)(i*VERTS + 3);
        	indices[i*INDS + 5] = (short)(i*VERTS + 0);
        }
        
        vertices = new float[numQuads*FLOATS*VERTS];
        
        for (int i = 0; i < verticesList.size(); ++i) {
        	vertices[i] = verticesList.get(i);
        }
	}
	
	private static final int AO_Quality = 3;
	
	private enum Axis {
		POS_X,
		NEG_X,
		POS_Y,
		NEG_Y,
		POS_Z,
		NEG_Z
	}
	
	private static float getAmbientOcclusion(int x, int y, int z, Axis axis, Neighborhood n) {
		return getAmbientOcclusion(x, y, z, axis, n, AO_Quality);
	}
	
	private static float getAmbientOcclusion(int x, int y, int z, Axis axis, Neighborhood n, int radius) {
		if (radius < 1) return 1;
		int cells = 0;
		
		int startX = -radius,
				endX = radius,
				startY = -radius,
				endY = radius,
				startZ = -radius,
				endZ = radius;
		
		switch (axis) {
			case NEG_X: endX = 0; break;
			case NEG_Y: endY = 0; break;
			case NEG_Z: endZ = 0; break;
			case POS_X: startX = 0; break;
			case POS_Y: startY = 0; break;
			case POS_Z: startZ = 0; break;
		}
		
		for (int i = startX; i < endX; ++i) {
			for (int j = startY; j < endY; ++j) {
				for (int k = startZ; k < endZ; ++k) {
					if (isOpaque(n.get(x+i, y+j, z+k))) cells++;
				}
			}
		}
		
		float proportion = (float)(cells/((Math.pow(radius*2, 3)/2)));
		
		float ao =  MathUtils.clamp(1 - proportion*1, 0, 1);
		return ao * (getAmbientOcclusion(x, y, z, axis, n, radius-1)+.25f)/1.25f;
	}
	
	public void dispose() {
		if (model != null) model.dispose();
	}
	
	public byte get(int x, int y, int z) {
		return data[x][y][z];
	}
	
	public static boolean isOpaque(byte datum) {
		return datum == 1;
	}
	
	public static boolean isVisible(byte datum) {
		return datum == 1;
	}
	
	public boolean collide(int x, int y, int z) {
		return data[x][y][z] == 1;
	}
	
	public int getX() {
		return regionX;
	}
	
	public int getZ() {
		return regionZ;
	}
	
	public Vector2 getCoords() {
		return new Vector2(regionX, regionZ);
	}
	
	public ModelInstance getModelInstance() {
		return instance;
	}
	
	public static Vector2 getRegionAt(float x, float z) {
		return new Vector2(MathUtils.floor(x/WIDTH), MathUtils.floor(z/WIDTH));
	}
	
	public static Vector2 getRegionAt(Vector2 coords, float x, float z) {
		coords.x = MathUtils.floor(x/WIDTH);
		coords.y = MathUtils.floor(z/WIDTH);
		return coords;
	}
	
	public static void appendQuad(Neighborhood n,
			ArrayList<Float> vertexList, 
			float x1, float y1, float z1,
			float x2, float y2, float z2,
			float x3, float y3, float z3,
			float x4, float y4, float z4,
			float r, float g, float b,
			Axis axis) {

		float ambientOcclusion1 = getAmbientOcclusion((int)x1, (int)y1, (int)z1, axis, n);
		float ambientOcclusion2 = getAmbientOcclusion((int)x2, (int)y2, (int)z2, axis, n);
		float ambientOcclusion3 = getAmbientOcclusion((int)x3, (int)y3, (int)z3, axis, n);
		float ambientOcclusion4 = getAmbientOcclusion((int)x4, (int)y4, (int)z4, axis, n);
		
		boolean flipped = (ambientOcclusion1 + ambientOcclusion3 < ambientOcclusion2 + ambientOcclusion4);

		float nx = 0, ny = 0, nz = 0;
		switch(axis) {
			case NEG_X: nx = -1; break;
			case NEG_Y: ny = -1; break;
			case NEG_Z: nz = -1; break;
			case POS_X: nx = 1; break;
			case POS_Y: ny = 1; break;
			case POS_Z: nz = 1; break;
		}
		
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
	
	public void setGenerationPriority(float newPriority) {
		this.generationPriority = newPriority;
	}

	@Override public int compareTo(Region o) {
		return (int)Math.signum(generationPriority - o.generationPriority);
	}
}
