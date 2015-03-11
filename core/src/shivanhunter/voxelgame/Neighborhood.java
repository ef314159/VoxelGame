package shivanhunter.voxelgame;

public class Neighborhood {
	private final Region
		center,
		north,
		northeast,
		east,
		southeast,
		south,
		southwest,
		west,
		northwest;
	
	public Neighborhood(
			Region center,
			Region north,
			Region northeast,
			Region east,
			Region southeast,
			Region south,
			Region southwest,
			Region west,
			Region northwest) {
		
		this.center = center;
		this.north = north;
		this.northeast = northeast;
		this.east = east;
		this.southeast = southeast;
		this.south = south;
		this.southwest = southwest;
		this.west = west;
		this.northwest = northwest;
	}
	
	public boolean isComplete() {
		return (center != null &&
				north != null &&
				northeast != null &&
				east != null &&
				southeast != null &&
				south != null &&
				southwest != null &&
				west != null &&
				northwest != null);
	}
	
	public byte get(int x, int y, int z) {
		boolean bNorth = false, bSouth = false, bEast = false, bWest = false;
		
		if (x < -Region.WIDTH || x >= 2*Region.WIDTH ||
				z < -Region.WIDTH || z >= 2*Region.WIDTH ||
				y < 0 || y >= Region.HEIGHT)
			return 0; //TODO error
		
		if (x > 0 && x < Region.WIDTH && z > 0 && z < Region.WIDTH) {
			return center.get(x, y, z);
		}
		
		if (x < 0) bWest = true;
		else if (x >= Region.WIDTH) bEast = true;
		
		if (z < 0) bSouth = true;
		else if (z >= Region.WIDTH) bNorth = true;
		
		if (bNorth && bEast) return northeast.get(x-Region.WIDTH, y, z-Region.WIDTH);
		else if (bNorth && bWest) return northwest.get(x+Region.WIDTH, y, z-Region.WIDTH);
		else if (bSouth && bWest) return southwest.get(x+Region.WIDTH, y, z+Region.WIDTH);
		else if (bSouth && bEast) return southeast.get(x-Region.WIDTH, y, z+Region.WIDTH);
		
		else if (bNorth) return north.get(x, y, z-Region.WIDTH);
		else if (bSouth) return south.get(x, y, z+Region.WIDTH);
		else if (bEast) return east.get(x-Region.WIDTH, y, z);
		else if (bWest) return west.get(x+Region.WIDTH, y, z);
		
		else return center.get(x, y, z);
	}
}
