package shivanhunter.voxelgame;

import java.util.LinkedList;
import java.util.List;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;

public class World implements ApplicationListener {
	private Environment environment;
	private PerspectiveCamera cam;
	private ModelBatch modelBatch;
	private SpriteBatch debugBatch;
	private int FOV = 85;
	private long seed;
	
	BitmapFont font;
	
	public float gravity = -32f;
	
	private int renderDistance = 18;
	private Map map;
	private List<Mob> mobs = new LinkedList<Mob>();
	
	private Player player;
	
	private Color fogColor;
	
	@Override public void create() {
		Region.loadDecoModels();
		
		fogColor = new Color(.0f, .25f, .75f, 1);
		modelBatch = new ModelBatch();
		debugBatch = new SpriteBatch();
		font = new BitmapFont();
		
		environment = new Environment();
		environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.75f, 0.75f, 0.75f, 1f));
		environment.set(new ColorAttribute(ColorAttribute.Fog, fogColor));
		//environment.add(new DirectionalLight().set(.15f, .15f, .15f, .1f, -1f, .05f));
        
        seed = System.currentTimeMillis();
        map = new Map(seed, renderDistance);
        
        /*for (int i = -127; i < 128; i += 32) {
            for (int j = -127; j < 128; j += 32) {
                mobs.add(new Mob(this, new Vector3(i, 32, j), .15f, .5f, 5f));
            }
        }*/
        
        player = new Player(this, new Vector3(64, 257, 1), .3f, 1.5f, 8f);
        mobs.add(player);
		
		cam = new PerspectiveCamera(FOV, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
		cam.near = .125f;
		cam.far = renderDistance*Region.WIDTH;
		
        Gdx.input.setInputProcessor(player);
        Gdx.input.setCursorCatched(true);
	}
	
	public float collide (Mob m) {
		int minX, maxX, minZ, maxZ;
		minX = MathUtils.floor(m.getPosition().x - m.getRadius());
		maxX = MathUtils.floor(m.getPosition().x + m.getRadius());
		
		minZ = MathUtils.floor(m.getPosition().z - m.getRadius());
		maxZ = MathUtils.floor(m.getPosition().z + m.getRadius());
		
		float maxHeight = -2000, height;
		
		for (int i = minX; i <= maxX; i++) {
			for (int j = minZ; j <= maxZ; j++) {
				height = getFloor(i, m.getPosition().y + m.getHeight(), j);
				if (maxHeight < height) maxHeight = height;
			}
		}
		
		return maxHeight;
	}
	
	private float getFloor(float x, float y, float z) {
		int regionX = MathUtils.floor(x/Region.WIDTH),
				regionZ = MathUtils.floor(z/Region.WIDTH);
		
		if (map.get(regionX, regionZ) == null) return y;
		
		int unitX = MathUtils.floor(x) % Region.WIDTH,
				unitY = MathUtils.floor(y),
				unitZ = MathUtils.floor(z) % Region.WIDTH;
		
		if (unitX < 0) unitX += Region.WIDTH;
		if (unitY >= Region.HEIGHT) unitY = Region.HEIGHT-1;
		if (unitY < 0) unitY = 0;
		if (unitZ < 0) unitZ += Region.WIDTH;
		
		if (map.get(regionX, regionZ).collide(unitX, unitY, unitZ)) {
			while (unitY < Region.HEIGHT && map.get(regionX, regionZ).collide(unitX, unitY, unitZ)) {
				unitY++;
			}
		} else {
			while (unitY >= 0 && !map.get(regionX, regionZ).collide(unitX, unitY, unitZ)) {
				unitY--;
			}
			unitY++;
		}
		return unitY;
	}
	
	@Override public void render() {
		MapCoord playerRegion = Region.getRegionAt(player.getPosition().x, player.getPosition().z);
		map.setPlayerRegion((int)playerRegion.x, (int)playerRegion.z);
		
		// go go gadget variable timestep
		float deltaTime = MathUtils.clamp(Gdx.graphics.getDeltaTime(), 0, .1f);
		//System.out.println(1/deltaTime);
		
        for (Mob m : mobs) {
			m.update(deltaTime);
        }
		
        // align camera with player mob
		cam.position.set(
				player.getPosition().x,
				player.getPosition().y + player.getHeight(),
				player.getPosition().z);
		
		cam.direction.set(
				MathUtils.cos(player.getAngleY()) * MathUtils.cos(player.getAngleX()),
				MathUtils.sin(player.getAngleY()),
				MathUtils.cos(player.getAngleY()) * MathUtils.sin(player.getAngleX()));
		
		cam.up.set(Vector3.Y);
		
		cam.update();
	
		
        //Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glClearColor(fogColor.r, fogColor.g, fogColor.b, fogColor.a);
 
        modelBatch.begin(cam);
        
        int playerX = MathUtils.floor(player.getPosition().x/Region.WIDTH),
        	playerZ = MathUtils.floor(player.getPosition().z/Region.WIDTH);
        
        for (int x = playerX-renderDistance; x <= playerX+renderDistance; ++x) {
        	for (int z = playerZ-renderDistance; z <= playerZ+renderDistance; ++z) {
        		if (Vector2.dst(x, z, playerX, playerZ) <= renderDistance) {
        	        map.get(x, z);
        		}
        	}
        }
        
        map.render(environment, modelBatch);
        
        for (Mob m : mobs) {
        	m.render(environment, modelBatch);
        }
        
        modelBatch.end();
        
        String debugText = 
        		"FPS: " + (int)(1/deltaTime) + "\n\n" +
        		"X: " + player.getPosition().x + "\n" +
        		"Y: " + player.getPosition().y + "\n" +
        		"Z: " + player.getPosition().z + "\n\n" +
                map.getDebugInfo();
        
		debugBatch.begin();
		font.drawMultiLine(debugBatch, debugText, 16, Gdx.graphics.getHeight() - 16);
		debugBatch.end();
	}
	
	/*
	 * Utility stuff
	 */
	@Override public void dispose() {
		modelBatch.dispose();
	}
	
	@Override public void resize(int width, int height) {
		cam.viewportHeight = height;
		cam.viewportWidth = width;
		cam.update();
	}

	public boolean needsGL20 () {
		return true;
	}
	
	/*
	 * Unused
	 */
	@Override public void pause() { }
	@Override public void resume() { }
}