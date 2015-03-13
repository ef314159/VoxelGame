package shivanhunter.voxelgame;

import com.badlogic.gdx.math.MathUtils;

public class RegionGenerator {
	/**
	 * Generates a byte array representing raw block data for a single Region.
	 * TODO: optimize
	 * 
	 * @param x the region's X coordinate
	 * @param z the region's Z coordinate
	 * @param seed the seed to use for generation
	 * @return the byte array representing raw block data
	 */
	public byte[][][] generate(int x, int z, long seed) {
		// get octave stuffs
		float[][][] data1 = generateOctave(x, z, 191, seed);
		float[][][] data2 = generateOctave(x, z, 111, seed);
		float[][][] data3 = generateOctave(x, z, 59, seed);
		float[][][] data4 = generateOctave(x, z, 31, seed);
		float[][][] data5 = generateOctave(x, z, 11, seed);
		float[][][] data6 = generateOctave(x, z, 5, seed);
		
		// allocate array to return
		byte[][][] cells = new byte[Region.WIDTH][Region.HEIGHT][Region.WIDTH];
		
		for (int i = 0; i < Region.WIDTH; ++i) {
			for (int j = 0; j < Region.HEIGHT; ++j) {
				for (int k = 0; k < Region.WIDTH; ++k) {
					// add together to get value, check if value is greater than a given amount
					if (data2[i][j][k]*7f +
							data2[i][j][k]*5f +
							data3[i][j][k] +
							data4[i][j][k]/2f +
							data5[i][j][k]/5f +
							data6[i][j][k]/8f -
							getHeightBias(j, .1f) >
							0f) {
						cells[i][j][k] = 1;
					}
					// else, if this is an open cell and the cell below is a block
					else if (j > 0 && cells[i][j-1][k] == 1) {
						// get another value to determine whether to place a deco object
						if (data5[i][j][k] +
								data6[i][j][k] +
								Noise.get(i, j, k, seed)*0.75f > 1f) {
							cells[i][j][k] = 2;
						}
					}
				}
			}
		}
		
		return cells;
	}
	
	/**
	 * Returns a bias based on the given height. The bias value is added to the
	 * noise data. To ensure that the bottom of a Region is solid and the top
	 * is open, the bias tends towards -INF as height goes to 0, and towards
	 * INF as height goes to Region.HEIGHT.
	 * 
	 * This may have to be put into a float array so that these calculations
	 * aren't being done for each block, but it would need to account for
	 * varying patterns of bias in different biomes (and interpolation between 
	 * these) once they are implemented.
	 * 
	 * @param height the height for which to return a bias
	 * @return the bias to be added to noise data
	 */
	public static float getHeightBias(int height, float flatness) {
		return (MathUtils.log2(height) - MathUtils.log2(Region.HEIGHT-1 - height))*flatness;
	}
	
	/**
	 * Generates noise data for a Region with a specific octave size. The
	 * result of this method for octaveSize <= 1 is noise data directly from
	 * Noise.get(). Otherwise, noise is interpolated between points at
	 * octaveSize-size intervals using trilinear interpolation.
	 * 
	 * @param x the region's x coordinate
	 * @param z the region's z coordinate
	 * @param octaveSize the size of the noise octave 
	 * @param seed the seed to use when getting noise data
	 * @return noise data for the given Region with the given octave size
	 */
	private float[][][] generateOctave(int x, int z, int octaveSize, long seed) {
		float[][][] data = new float[Region.WIDTH][Region.HEIGHT][Region.WIDTH];
		
		if (octaveSize < 2) {
			// if octaves are only 1 cell, we don't need the fancy stuff
			// handle size < 1 as well for sanity
			for (int i = x; i < x+Region.WIDTH; ++i) {
				for (int j = 0; j < Region.HEIGHT; ++j) {
					for (int k = z; k < z+Region.WIDTH; ++k) {
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
	
	/**
	 * Performs trilinear interpolation, given eight sampling points at the
	 * corners of a cube, and the distance along the cube in x, y and z
	 * 
	 * @param v000 the value at x0, y0, z0
	 * @param v001 the value at x0, y0, z1
	 * @param v010 the value at x0, y1, z0
	 * @param v011 the value at x0, y1, z1
	 * @param v100 the value at x1, y0, z0
	 * @param v101 the value at x1, y0, z1
	 * @param v110 the value at x1, y1, z0
	 * @param v111 the value at x1, y1, z1
	 * @param x the position between x0 and x1, from 0 to 1, to sample
	 * @param y the position between y0 and y1, from 0 to 1, to sample
	 * @param z the position between z0 and z1, from 0 to 1, to sample
	 * @return the interpolation from the given values at the given position
	 */
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
	
	/**
	 * Rounds down in a given interval.
	 * 
	 * @param x the value to round
	 * @param range the size of the interval in which to round
	 * @return the rounded value
	 */
	private static int floorRange(float x, float range) {
		return (int)(Math.floor(x/range)*range);
	}
}
