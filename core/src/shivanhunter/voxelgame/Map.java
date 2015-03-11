package shivanhunter.voxelgame;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.Material;
import com.badlogic.gdx.graphics.g3d.Model;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;

public class Map {
	private static final boolean RENDERDEBUG = false;
	
	private PriorityQueue<MapCoord> toCreate = new PriorityQueue<MapCoord>();
	private Collection<MapCoord> regionsInProgress = new LinkedList<MapCoord>();
	private BlockingQueue<Region> newRegions = new LinkedBlockingQueue<Region>();
	
	private HashMap<MapCoord, Region> map = new HashMap<MapCoord, Region>();

	private Collection<Region> waitingForNeighbors = new LinkedList<Region>();
	private PriorityQueue<Region> toCreateMesh = new PriorityQueue<Region>();
	private Collection<Region> meshesInProgress = new LinkedList<Region>();
	private BlockingQueue<Region> newMeshes = new LinkedBlockingQueue<Region>();
	
	private int activeThreads = 0;
	private static final int maxThreads = 6;
	private MapCoord playerRegion;
	private final int range;
	private long seed;
	
	private Model toCreateIndicator;
	
	/**
	 * Constructs a Map given a seed and a rendering range. The map will
	 * generate regions in a range*2+1 by range*2+1 area.
	 * 
	 * @param seed the seed to use when generating regions
	 * @param range the rendering radius
	 */
	public Map(long seed, int range) {
		this.seed = seed;
		this.range = range;
		this.playerRegion = new MapCoord(0, 0, 0);
		
		ModelBuilder builder = new ModelBuilder();
		toCreateIndicator = builder.createBox(8, 8, 8,
				new Material(ColorAttribute.createDiffuse(1f, 1f, 1f, 1)),
				 Usage.Position | Usage.Normal);
	}
	
	public void setPlayerRegion(int x, int z) {
		playerRegion = new MapCoord(x, z, 0);
		
		Collection<MapCoord> tempToCreate = new LinkedList<MapCoord>();
		Collection<Region> tempToCreateMesh = new LinkedList<Region>();
		
		for (MapCoord coord : toCreate) {
			coord.generationPriority = playerRegion.distance(coord.x, coord.z);
			tempToCreate.add(coord);
		}
		for (Region r : toCreateMesh) {
			tempToCreateMesh.add(r);
		}
		for (Region r : map.values()) {
			r.setGenerationPriority(playerRegion.distance(r.getX(), r.getZ()));
		}
		
		toCreate.clear();
		toCreateMesh.clear();
		
		for (MapCoord coord : tempToCreate) {
			toCreate.add(coord);
		}
		for (Region r : tempToCreateMesh) {
			toCreateMesh.add(r);
		}
		
		//forceGenerate(x, z);
	}
	
	public Region get(int x, int z) {
		return map.get(new MapCoord(x, z, -1));
	}
	
	public String getDebugInfo() {
		return "toCreate: " + toCreate.size() + "\n" + 
				"regionsInProgress: " + regionsInProgress.size() + "\n" +
				"newRegions: " + newRegions.size() + "\n" +
				"waitingForNeighbors: " + waitingForNeighbors.size() + "\n" +
				"toCreateMesh: " + toCreateMesh.size() + "\n" +
				"meshesInProgress: " + meshesInProgress.size() + "\n" +
				"newMeshes: " + newMeshes.size() + "\n" +
				"map: " + map.size() + "\n" +
				"threads: " + activeThreads + "/" + maxThreads + "\n";
	}
	
	public void render(Environment environment, ModelBatch batch) {
		Collection<Region> regionsToRemove = new LinkedList<Region>();
		Collection<MapCoord> coordsToRemove = new LinkedList<MapCoord>();
		
		for (int i = playerRegion.x - range; i <= playerRegion.x + range; ++i) {
			for (int j = playerRegion.z - range; j <= playerRegion.z + range; ++j) {
				generate(i, j);
			}
		}
		
		Region newRegion;
		
		while ((newRegion = newRegions.poll()) != null) {
			MapCoord coord = new MapCoord(
					newRegion.getX(), newRegion.getZ(),
					playerRegion.distance(newRegion.getX(), newRegion.getZ()));
			map.put(coord, newRegion);
			regionsInProgress.remove(coord);
			waitingForNeighbors.add(newRegion);
		}
		
		for (Region r : waitingForNeighbors) {
			if (getNeighborhood(r).isComplete()) {
				toCreateMesh.add(r);
				regionsToRemove.add(r);
			}
		}
		
		for (Region r : regionsToRemove) {
			waitingForNeighbors.remove(r);		
		}
		
		regionsToRemove.clear();

		while (activeThreads < maxThreads) {
			if (startThread()) {
				activeThreads++;
			} else break;
		}
		
		Region newMesh;
		
		while ((newMesh = newMeshes.poll()) != null) {
			meshesInProgress.remove(newMesh);
			newMesh.loadMesh();
		}
		
		for (Region r : map.values()) {
			if (r.getX() < playerRegion.x - range || r.getX() > playerRegion.x + range ||
					r.getZ() < playerRegion.z - range || r.getZ() > playerRegion.z + range) {
				regionsToRemove.add(r);
			} else {
				if (r.getModelInstance() != null) {
					//batch.render(r.getModelInstance(), environment);
					boolean drawDeco = playerRegion.distance(r.getX(), r.getZ()) < range/3f;
					r.render(environment, batch, drawDeco);
				}
			}
		}
		
		for (MapCoord coord : toCreate) {
			if (coord.x < playerRegion.x - range || coord.x > playerRegion.x + range ||
				coord.z < playerRegion.z - range || coord.z > playerRegion.z + range) {
				coordsToRemove.add(coord);
			}
		}
		
		if (RENDERDEBUG) {
			for (MapCoord coord : toCreate) {
				ModelInstance box = new ModelInstance(toCreateIndicator);
				box.transform.translate(
						coord.x*Region.WIDTH + Region.WIDTH/2,
						16,
						coord.z*Region.WIDTH + Region.WIDTH/2);
				box.materials.get(0).set(ColorAttribute.createDiffuse(new Color(
								8f/coord.generationPriority,
								2f/coord.generationPriority,
								0,
								1)));
				batch.render(box);
			}
		
			for (Region r : waitingForNeighbors) {
				ModelInstance box = new ModelInstance(toCreateIndicator);
				box.transform.translate(
						r.getX()*Region.WIDTH + Region.WIDTH/2,
						16,
						r.getZ()*Region.WIDTH + Region.WIDTH/2);
				box.materials.get(0).set(ColorAttribute.createDiffuse(new Color(0, 0.5f, 1, 1)));
				batch.render(box);
			}
		
			for (Region r : toCreateMesh) {
				ModelInstance box = new ModelInstance(toCreateIndicator);
				box.transform.translate(
						r.getX()*Region.WIDTH + Region.WIDTH/2,
						16,
						r.getZ()*Region.WIDTH + Region.WIDTH/2);
				box.materials.get(0).set(ColorAttribute.createDiffuse(new Color(0, 1, 1, 1)));
				batch.render(box);
			}
		}
		
		for (Region r : regionsToRemove) {
			map.remove(new MapCoord(r.getX(), r.getZ(), -1));
			waitingForNeighbors.remove(r);
			toCreateMesh.remove(r);
		}
		
		for (MapCoord coord : coordsToRemove) {
			toCreate.remove(coord);
		}
	}
	
	public void generate(int x, int z) {
		MapCoord coords = new MapCoord(x, z, playerRegion.distance(x, z));
		if (!map.containsKey(coords) &&
				!toCreate.contains(coords) &&
				!regionsInProgress.contains(coords)) {
			toCreate.add(coords);
		}
	}
	
	private class RegionCreator extends Thread {
		private final MapCoord coords;
		
		public RegionCreator(MapCoord coords) {
			this.coords = coords;
		}
		
		public void run() {
			try {
				newRegions.put(new Region(coords.x, coords.z, seed, playerRegion.distance(coords)));
				activeThreads--;
				//toCreate.remove(coords);
			} catch (InterruptedException e) {
				System.err.println("Thread interrupted when creating a region at (" + coords.x + ", " + coords.z + ")");
			}
		}
	}
	
	private boolean startThread() {
		if (!toCreate.isEmpty() && !toCreateMesh.isEmpty()) {
			MapCoord toCreateCoord = toCreate.peek();
			Region toCreateMeshRegion = toCreateMesh.peek();
			
			if (toCreateMeshRegion.generationPriority < toCreateCoord.generationPriority) {
				if (startThread(toCreateMeshRegion)) {
					return true;
				}
			}
			startThread(toCreateCoord);
			return true;
		}
		else if (!toCreate.isEmpty()) {
			startThread(toCreate.peek());
			return true;
		}
		else if (!toCreateMesh.isEmpty()) {
			return startThread(toCreateMesh.peek());
		}
		return false;
	}
	
	private void startThread(MapCoord coords) {
		regionsInProgress.add(coords);
		toCreate.remove(coords);
		new RegionCreator(coords).start();
	}
	
	private boolean startThread(Region r) {
		Neighborhood n = getNeighborhood(r);
		if (n.isComplete()) {
			meshesInProgress.add(r);
			new RegionMeshCreator(r, n).start();
			toCreateMesh.remove(r);
			return true;
		} else {
			waitingForNeighbors.add(r);
			toCreateMesh.remove(r);
			return false;
		}
	}
	
	private Neighborhood getNeighborhood (Region r) {
		return new Neighborhood(
				r,
				map.get(new MapCoord(r.getX(), r.getZ()+1, -1)),
				map.get(new MapCoord(r.getX()+1, r.getZ()+1, -1)),
				map.get(new MapCoord(r.getX()+1, r.getZ(), -1)),
				map.get(new MapCoord(r.getX()+1, r.getZ()-1, -1)),
				map.get(new MapCoord(r.getX(), r.getZ()-1, -1)),
				map.get(new MapCoord(r.getX()-1, r.getZ()-1, -1)),
				map.get(new MapCoord(r.getX()-1, r.getZ(), -1)),
				map.get(new MapCoord(r.getX()-1, r.getZ()+1, -1))
				);
	}
	
	private class RegionMeshCreator extends Thread {
		private final Region r;
		private final Neighborhood n;
		
		public RegionMeshCreator(Region r, Neighborhood n) {
			this.r = r;
			this.n = n;
		}
		
		public void run() {
			try {
				if (n.isComplete()) {
					r.createMesh(n);
					newMeshes.put(r);
					activeThreads--;
				}
			} catch (InterruptedException e) {
				System.err.println("Thread interrupted when meshing a region at (" + r.getX() + ", " + r.getZ() + ")");
			}
		}
	}
	
	private class MapCoord implements Comparable<MapCoord> {
		public final int x, z;
		public float generationPriority;
		
		public MapCoord(int x, int z, float generationPriority) {
			this.x = x;
			this.z = z;
			this.generationPriority = generationPriority;
		}
		
		public float distance(MapCoord other) {
			return distance(other.x, other.z);
		}
		
		public float distance(float otherX, float otherZ) {
			float dx = x - otherX, dz = z - otherZ;
			return (float)Math.sqrt(dx*dx + dz*dz);
			//return Math.max(Math.abs(dx), Math.abs(dz));
		}
		
		@Override public int hashCode() {
			//Szudzik's function, using x and z mapped from the integers to the natural numbers
			int a = (x >= 0 ? x*2 : -x*2 - 1);
			int b = (z >= 0 ? z*2 : -z*2 - 1);
			return a >= b ? a * a + a + b : a + b * b;
		}
		
		@Override public boolean equals(Object other) {
			if (other != null & other instanceof MapCoord) {
				MapCoord otherCoord = (MapCoord)other;
				return (otherCoord.x == x && otherCoord.z == z);
			}
			
			return false;
		}

		@Override public int compareTo(MapCoord o) {
			return (int)Math.signum(generationPriority - o.generationPriority);
		}
	}
}


