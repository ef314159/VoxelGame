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
		float[][][] data1 = generate3DOctave(x, z, 127, seed, -1, 1);
		float[][][] data2 = generate3DOctave(x, z, 69, seed, -1, 1);
		float[][][] data3 = generate3DOctave(x, z, 29, seed, -1, 1);
		float[][][] data4 = generate3DOctave(x, z, 13, seed, -1, 1);
		
		float[][] temperature = new float[Region.WIDTH][Region.WIDTH],
				wetness = new float[Region.WIDTH][Region.WIDTH],
				noise = generate2DFloorOctave(x, z, 256, seed, 0, .75f),
				strangeness = new float[Region.WIDTH][Region.WIDTH];
		
		for (int i = 0; i < Region.WIDTH; ++i) {
			for (int k = 0; k < Region.WIDTH; ++k) {
				noise[i][k] = noise[i][k]*noise[i][k];
				noise[i][k] += (float)Math.pow(Math.abs((x+i)/10000f), 3) + .01f;
				//System.out.println(x+i + "  " + noise[i][k]);
			}
		}
		
		// allocate array to return
		byte[][][] cells = new byte[Region.WIDTH][Region.HEIGHT][Region.WIDTH];
		
		for (int i = 0; i < Region.WIDTH; ++i) {
			for (int j = 0; j < Region.HEIGHT; ++j) {
				for (int k = 0; k < Region.WIDTH; ++k) {
					// add together to get value, check if value is greater than a given amount
					if (data1[i][j][k]*3f
							+ data2[i][j][k]*4f
							+ data3[i][j][k]
							+ data4[i][j][k]/3f
							- getHeightBias(j, noise[i][k])
						//	+ ((j%32)*(j%32))/400f*Math.max(data1[i][j][k]+0.25f, 0)
							> 0f) {
						cells[i][j][k] = 1;
					}
					// else, if this is an open cell and the cell below is a block
					else if (j > 0 && cells[i][j-1][k] == 1) {
						// get another value to determine whether to place a deco object
						float value = data3[i][j][k] + data4[i][j][k] + Noise.get(i, j, k, seed);
						if (value > 1.5f) {
							byte model = (byte)Noise.get(i, j+1, k, seed, 4);
							//System.out.println(model);
							cells[i][j][k] = (byte)(2+model);
						} else {
							float value2 = data3[i][j][k] + Noise.get(i, j, k, seed);
							if (value2 > 1.5f) {
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
	 * @param noise the desired variation in height of the landscape
	 * @return the bias to be added to noise data
	 */
	public static float getHeightBias(int height, float noise) {
		return (MathUtils.log2(height) - MathUtils.log2(Region.HEIGHT-1 - height))/noise;
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
	 * @param min the minimum value of noise
	 * @param max the maximum value of noise
	 * @return noise data for the given Region with the given octave size
	 */
	private float[][][] generate3DOctave(int x, int z, int octaveSize, long seed, float min, float max) {
		float[][][] data = new float[Region.WIDTH][Region.HEIGHT][Region.WIDTH];
		
		if (octaveSize < 2) {
			// if octaves are only 1 cell, we don't need the fancy stuff
			// handle size < 1 as well for sanity
			for (int i = x; i < x+Region.WIDTH; ++i) {
				for (int j = 0; j < Region.HEIGHT; ++j) {
					for (int k = z; k < z+Region.WIDTH; ++k) {
						data[i-x][j-z][k-z] = Noise.get(i, j, k, seed)*(max-min) + min;
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
											seed)*(max-min) + min;
								}
							}
						}
						
						float xInterval, yInterval, zInterval;
						float v00z, v01z, v10z, v11z, v0yz, v1yz;
						
						for (int k = zmin; k < zmax; ++k) {
							
							zInterval = (k-octZ)/(float)octaveSize;

							v00z = interpolate(corners[0][0][0], corners[0][0][1], zInterval);
							v01z = interpolate(corners[0][1][0], corners[0][1][1], zInterval);
							v10z = interpolate(corners[1][0][0], corners[1][0][1], zInterval);
							v11z = interpolate(corners[1][1][0], corners[1][1][1], zInterval);
							
							for (int j = ymin; j < ymax; ++j) {

								yInterval = (j-octY)/(float)octaveSize;
								v0yz = interpolate(v00z, v01z, yInterval);
								v1yz = interpolate(v10z, v11z, yInterval);
								
								for (int i = xmin; i < xmax; ++i) {
									/*data[i-x][j][k-z] = trilinearInterpolation(
											corners,
											(i-octX)/(float)octaveSize,
											(j-octY)/(float)octaveSize,
											(k-octZ)/(float)octaveSize);*/

									xInterval = (i-octX)/(float)octaveSize;
									data[i-x][j][k-z] = interpolate(v0yz, v1yz, xInterval);
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
	 * Generates 2D noise data only in X and Z for a Region with a specific
	 * octave size. The result of this method for octaveSize <= 1 is noise data
	 * directly from Noise.get(). Otherwise, noise is interpolated between
	 * points at octaveSize-size intervals using trilinear interpolation.
	 * 
	 * @param x the lowest block coordinate in x of the region
	 * @param z the lowest block coordinate in z of the region
	 * @param octaveSize the size of the noise octave 
	 * @param seed the seed to use when getting noise data
	 * @param min the minimum value of noise
	 * @param max the maximum value of noise
	 * @return noise data for the given Region with the given octave size
	 */
	private float[][] generate2DFloorOctave(int x, int z, int octaveSize, long seed, float min, float max) {
		float[][] data = new float[Region.WIDTH][Region.WIDTH];
		
		if (octaveSize < 2) {
			// if octaves are only 1 cell, we don't need the fancy stuff
			// handle size < 1 as well for sanity
			for (int i = x; i < x+Region.WIDTH; ++i) {
				for (int k = z; k < z+Region.WIDTH; ++k) {
					data[i-x][k-z] = Noise.get(i, 1337, k, seed)*(max-min) + min;
				}
			}
		} else {
			int xmin, xmax, zmin, zmax;
			float[][] corners = new float[2][2];
			
			// for each octave in or partly in the region
			for (int octX = floorRange(x, octaveSize); octX < x+Region.WIDTH; octX += octaveSize) {
				for (int octZ = floorRange(z, octaveSize); octZ < z+Region.WIDTH; octZ += octaveSize) {

					xmin = Math.max(octX, x);
					xmax = Math.min(octX + octaveSize, x + Region.WIDTH);
					zmin = Math.max(octZ, z);
					zmax = Math.min(octZ + octaveSize, z + Region.WIDTH);
					
					for (int i = 0; i < 2; i++) {
						for (int k = 0; k < 2; k++) {
							corners[i][k] = Noise.get(
									octX + i*octaveSize,
									1337,
									octZ + k*octaveSize,
									seed)*(max-min) + min;
						}
					}
					
					float xInterval, zInterval;
					float v0z, v1z;
					
					for (int k = zmin; k < zmax; ++k) {
						
						zInterval = (k-octZ)/(float)octaveSize;
						v0z = interpolate(corners[0][0], corners[0][1], zInterval);
						v1z = interpolate(corners[1][0], corners[1][1], zInterval);
						
						for (int i = xmin; i < xmax; ++i) {

							xInterval = (i-octX)/(float)octaveSize;
							data[i-x][k-z] = interpolate(v0z, v1z, xInterval);
						}
					}
				}
			}
		}
		
		return data;
	}
	
	/**
	 * Uses trig interpolation to interpolate smoothly between two values.
	 * 
	 * @param value1 the first value
	 * @param value2 the second value
	 * @param alpha a number from 0 to 1 for interpolating
	 * @return the result of interpolation
	 */
	private static float interpolate(float value1, float value2, float alpha) {
		//return value1 + (value2-value1)*alpha;
		
		float alpha2 = (1-MathUtils.cos(alpha*MathUtils.PI))/2;
		return value1*(1-alpha2) + value2*alpha2;
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
