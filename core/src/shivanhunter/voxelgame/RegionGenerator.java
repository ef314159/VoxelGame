package shivanhunter.voxelgame;

import com.badlogic.gdx.math.MathUtils;

public class RegionGenerator {
	public byte[][][] generate(int x, int z, long seed) {
		float[][][] data1 = generateOctave(x, z, 257, seed);
		float[][][] data2 = generateOctave(x, z, 125, seed);
		float[][][] data3 = generateOctave(x, z, 59, seed);
		float[][][] data4 = generateOctave(x, z, 31, seed);
		float[][][] data5 = generateOctave(x, z, 11, seed);
		
		byte[][][] cells = new byte[Region.WIDTH][Region.HEIGHT][Region.WIDTH];
		
		for (int i = 0; i < Region.WIDTH; ++i) {
			for (int j = 0; j < Region.HEIGHT; ++j) {
				for (int k = 0; k < Region.WIDTH; ++k) {
					if (data1[i][j][k]*8f +
							data2[i][j][k]*3f +
							data3[i][j][k] +
							data4[i][j][k]/2f +
							data5[i][j][k]/6f -
							getHeightBias(j) >
							6f) {
						cells[i][j][k] = 1;
					} else if (j > 0) {
						float data = data4[i][j][k] + data5[i][j][k] + Noise.get(i, j, k, seed)*.4f;
						if (cells[i][j-1][k] == 1 && data > 1.65f) {
							cells[i][j][k] = 2;
						}
					}
				}
			}
		}
		
		return cells;
	}
	
	public static float getHeightBias(int height) {
		return MathUtils.log2(height) - MathUtils.log2(Region.HEIGHT-height);
	}
	
	private float[][][] generateOctave(int x, int z, int octaveSize, long seed) {
		float[][][] data = new float[Region.WIDTH+1][Region.HEIGHT+1][Region.WIDTH+1];
		
		if (octaveSize < 2) {
			// if octaves are only 1 cell, we don't need the fancy stuff
			// handle size < 1 as well for sanity
			for (int i = x; i < x+Region.WIDTH+1; ++i) {
				for (int j = 0; j < Region.HEIGHT+1; ++j) {
					for (int k = z; k < z+Region.WIDTH+1; ++k) {
						data[i-x][j-z][k-z] = Noise.get(i, j, k, seed);
					}
				}
			}
		} else {
			for (int i = x; i < x+Region.WIDTH; ++i) {
				for (int j = 0; j < Region.HEIGHT; ++j) {
					for (int k = z; k < z+Region.WIDTH; ++k) {
						
						int octaveX0 = floorRange(i, octaveSize),
								octaveY0 = floorRange(j, octaveSize),
								octaveZ0 = floorRange(k, octaveSize);
						
						int octaveX1 = octaveX0 + octaveSize,
								octaveY1 = octaveY0 + octaveSize,
								octaveZ1 = octaveZ0 + octaveSize;
						
						float interpX = (i-octaveX0)/(float)octaveSize,
								interpY = (j-octaveY0)/(float)octaveSize,
								interpZ = (k-octaveZ0)/(float)octaveSize;
						
						data[i-x][j][k-z] = Noise.get(i, j, k, seed);
						data[i-x][j][k-z] = trilinearInterpolation(
								Noise.get(octaveX0, octaveY0, octaveZ0, seed),
								Noise.get(octaveX0, octaveY0, octaveZ1, seed),
								Noise.get(octaveX0, octaveY1, octaveZ0, seed),
								Noise.get(octaveX0, octaveY1, octaveZ1, seed),
								Noise.get(octaveX1, octaveY0, octaveZ0, seed),
								Noise.get(octaveX1, octaveY0, octaveZ1, seed),
								Noise.get(octaveX1, octaveY1, octaveZ0, seed),
								Noise.get(octaveX1, octaveY1, octaveZ1, seed),
								interpX, interpY, interpZ);
					}
				}
			}
			
		}
		
		return data;
	}
	
	private static float trilinearInterpolation(
			float v000, float v001, float v010, float v011,
			float v100, float v101, float v110, float v111,
			float x, float y, float z) {
		
		float v00z = v000 + (v001 - v000)*z,
				v01z = v010 + (v011 - v010)*z,
				v10z = v100 + (v101 - v100)*z,
				v11z = v110 + (v111 - v110)*z;
		
		float v0yz = v00z + (v01z - v00z)*y,
				v1yz = v10z + (v11z - v10z)*y;
		
		float vxyz = v0yz + (v1yz - v0yz)*x;
		
		return vxyz;
	}
	
	private static int floorRange(float x, float range) {
		return (int)(Math.floor(x/range)*range);
	}
}
