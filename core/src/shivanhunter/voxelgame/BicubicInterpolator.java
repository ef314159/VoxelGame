package shivanhunter.voxelgame;

/**
 * Provides several static functions for interpolating 2d noise. Code is adapted
 * (read: ripped off) from http://www.paulinternet.nl/?page=bicubic.
 * 
 * Maintains internal state (coefficient array which is constant within a given
 * octave region); usage is to generate the necessary coefficients for that region,
 * pass them in using updateCoefficients(), then use getValue for any cell inside
 * the region. This is faster than updating the array each time a value is
 * calculated, which fits the use case (octave noise) well. 
 *
 */
public class BicubicInterpolator {
	private static float[][] c = new float[4][4];
	
	public static void updateCoefficients(float[][] p) {
		c[0][0] = p[1][1];
		c[0][1] = -.5f*p[1][0] + .5f*p[1][2];
		c[0][2] = p[1][0]      - 2.5f*p[1][1]  + 2*p[1][2]    - .5f*p[1][3];
		c[0][3] = -.5f*p[1][0] + 1.5f*p[1][1]  - 1.5f*p[1][2] + .5f*p[1][3];
		c[1][0] = -.5f*p[0][1] + .5f*p[2][1];
		c[1][1] = .25f*p[0][0] - .25f*p[0][2]  - .25f*p[2][0] + .25f*p[2][2];
		c[1][2] = -.5f*p[0][0] + 1.25f*p[0][1] - p[0][2]      + .25f*p[0][3]   + .5f*p[2][0]   - 1.25f*p[2][1] + p[2][2]       - .25f*p[2][3];
		c[1][3] = .25f*p[0][0] - .75f*p[0][1]  + .75f*p[0][2] - .25f*p[0][3]   - .25f*p[2][0]  + .75f*p[2][1]  - .75f*p[2][2]  + .25f*p[2][3];
		c[2][0] = p[0][1]      - 2.5f*p[1][1]  + 2*p[2][1]    - .5f*p[3][1];
		c[2][1] = -.5f*p[0][0] + .5f*p[0][2]   + 1.25f*p[1][0] - 1.25f*p[1][2] - p[2][0]       + p[2][2]       + .25f*p[3][0]  - .25f*p[3][2];
		c[2][2] = p[0][0]      - 2.5f*p[0][1]  + 2*p[0][2]    - .5f*p[0][3]    - 2.5f*p[1][0]  + 6.25f*p[1][1] - 5*p[1][2]     + 1.25f*p[1][3] + 2*p[2][0]    - 5*p[2][1]     + 4*p[2][2]     - p[2][3]      - .5f*p[3][0]  + 1.25f*p[3][1] - p[3][2]      + .25f*p[3][3];
		c[2][3] = -.5f*p[0][0] + 1.5f*p[0][1]  - 1.5f*p[0][2] + .5f*p[0][3]    + 1.25f*p[1][0] - 3.75f*p[1][1] + 3.75f*p[1][2] - 1.25f*p[1][3] - p[2][0]      + 3*p[2][1]     - 3*p[2][2]     + p[2][3]      + .25f*p[3][0] - .75f*p[3][1]  + .75f*p[3][2] - .25f*p[3][3];
		c[3][0] = -.5f*p[0][1] + 1.5f*p[1][1]  - 1.5f*p[2][1] + .5f*p[3][1];
		c[3][1] = .25f*p[0][0] - .25f*p[0][2]  - .75f*p[1][0] + .75f*p[1][2]   + .75f*p[2][0]  - .75f*p[2][2]  - .25f*p[3][0]  + .25f*p[3][2];
		c[3][2] = -.5f*p[0][0] + 1.25f*p[0][1] - p[0][2]      + .25f*p[0][3]   + 1.5f*p[1][0]  - 3.75f*p[1][1] + 3*p[1][2]     - .75f*p[1][3]  - 1.5f*p[2][0] + 3.75f*p[2][1] - 3*p[2][2]     + .75f*p[2][3] + .5f*p[3][0]  - 1.25f*p[3][1] + p[3][2]      - .25f*p[3][3];
		c[3][3] = .25f*p[0][0] - .75f*p[0][1]  + .75f*p[0][2] - .25f*p[0][3]   - .75f*p[1][0]  + 2.25f*p[1][1] - 2.25f*p[1][2] + .75f*p[1][3]  + .75f*p[2][0] - 2.25f*p[2][1] + 2.25f*p[2][2] - .75f*p[2][3] - .25f*p[3][0] + .75f*p[3][1]  - .75f*p[3][2] + .25f*p[3][3];
	}
	
	public static float getValue(float x, float y) {
		float x2 = x * x;
		float x3 = x2 * x;
		float y2 = y * y;
		float y3 = y2 * y;

		return (c[0][0] + c[0][1] * y + c[0][2] * y2 + c[0][3] * y3) +
		       (c[1][0] + c[1][1] * y + c[1][2] * y2 + c[1][3] * y3) * x +
		       (c[2][0] + c[2][1] * y + c[2][2] * y2 + c[2][3] * y3) * x2 +
		       (c[3][0] + c[3][1] * y + c[3][2] * y2 + c[3][3] * y3) * x3;
	}
}
