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
		float[][][] data1 = generateOctave(x, z, 65, seed);
		float[][][] data2 = generateOctave(x, z, 41, seed);
		float[][][] data3 = generateOctave(x, z, 23, seed);
		float[][][] data4 = generateOctave(x, z, 14, seed);
		float[][][] data5 = generateOctave(x, z, 9, seed);
		float[][][] data6 = generateOctave(x, z, 3, seed);
		
		// allocate array to return
		byte[][][] cells = new byte[Region.WIDTH][Region.HEIGHT][Region.WIDTH];
		
		for (int i = 0; i < Region.WIDTH; ++i) {
			for (int j = 0; j < Region.HEIGHT; ++j) {
				for (int k = 0; k < Region.WIDTH; ++k) {
					// add together to get value, check if value is greater than a given amount
					if (data1[i][j][k]*3f
							+ data2[i][j][k]*6f
							+ data3[i][j][k]
							+ data4[i][j][k]/2f
							+ data5[i][j][k]/4f
							+ data6[i][j][k]/8f
							- getHeightBias(j, 4f)
						//	+ ((j%32)*(j%32))/400f*Math.max(data1[i][j][k]+0.25f, 0)
							> 0f) {
						cells[i][j][k] = 1;
					}
					// else, if this is an open cell and the cell below is a block
					else if (j > 0 && cells[i][j-1][k] == 1) {
						// get another value to determine whether to place a deco object
						float value = data5[i][j][k] + data6[i][j][k] + Noise.get(i, j, k, seed)*0.75f;
						if (value > 1) {
							byte model = (byte)Noise.get(i, j+1, k, seed, 4);
							//System.out.println(model);
							cells[i][j][k] = (byte)(2+model);
						} else {
							float value2 = data4[i][j][k] + Noise.get(i, j, k, seed)*0.2f;
							if (value2 > .5f) {
								byte model = (byte)Noise.get(i, j+1, k, seed, 4);
								cells[i][j][k] = (byte)(2+4+model);
							} 
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
	 * @param x the lowest block coordinate in x of the region
	 * @param z the lowest block coordinate in z of the region
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
			int xmin, xmax, ymin, ymax, zmin, zmax;
			float[][][] corners = new float[2][2][2];
			
			// for each octave in or partly in the region
			for (int octX = floorRange(x, octaveSize); octX < x+Region.WIDTH; octX += octaveSize) {
				for (int octY = floorRange(0, octaveSize); octY < Region.HEIGHT; octY += octaveSize) {
					for (int octZ = floorRange(z, octaveSize); octZ < z+Region.WIDTH; octZ += octaveSize) {

						xmin = Math.max(octX, x);
						xmax = Math.min(octX + octaveSize, x + Region.WIDTH);
						ymin = Math.max(octY, 0);
						ymax = Math.min(octY + octaveSize, Region.HEIGHT);
						zmin = Math.max(octZ, z);
						zmax = Math.min(octZ + octaveSize, z + Region.WIDTH);
						
						for (int i = 0; i < 2; i++) {
							for (int j = 0; j < 2; j++) {
								for (int k = 0; k < 2; k++) {
									corners[i][j][k] = Noise.get(
											octX + i*octaveSize,
											octY + j*octaveSize,
											octZ + k*octaveSize,
											seed);
								}
							}
						}
						
						for (int i = xmin; i < xmax; ++i) {
							for (int j = ymin; j < ymax; ++j) {
								for (int k = zmin; k < zmax; ++k) {
									data[i-x][j][k-z] = trilinearInterpolation(
											corners,
											(i-octX)/(float)octaveSize,
											(j-octY)/(float)octaveSize,
											(k-octZ)/(float)octaveSize);
								}
							}
						}
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
	 * @param values the 3D array containing the noise values at the eight cube corners
	 * @param x the position between x0 and x1, from 0 to 1, to sample
	 * @param y the position between y0 and y1, from 0 to 1, to sample
	 * @param z the position between z0 and z1, from 0 to 1, to sample
	 * @return the interpolation from the given values at the given position
	 */
	private static float trilinearInterpolation(float[][][] values, float x, float y, float z) {
		
		float v00z = values[0][0][0] + (values[0][0][1] - values[0][0][0])*z,
				v01z = values[0][1][0] + (values[0][1][1] - values[0][1][0])*z,
				v10z = values[1][0][0] + (values[1][0][1] - values[1][0][0])*z,
				v11z = values[1][1][0] + (values[1][1][1] - values[1][1][0])*z;
		
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
