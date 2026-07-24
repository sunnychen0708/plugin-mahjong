import com.mahjongplay.tw.TaiwanMahjongPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.*;
import org.joml.Matrix4f;

import java.io.File;
import java.nio.file.Files;
import java.util.*;

public final class PhysicalTableSmokeTest {
  public static void main(String[] args) throws Exception {
    MockWorld world = new MockWorld("world");
    Bukkit.__setWorld("world", world);
    MockPlayer player = new MockPlayer(world);
    Bukkit.__setPlayer(player.id, player);
    JavaPlugin.__setServer(new MockServer());
    JavaPlugin.__setCommand(new PluginCommand());
    File folder = Files.createTempDirectory("mahjongplay-smoke").toFile();
    JavaPlugin.__setDataFolder(folder);

    TaiwanMahjongPlugin plugin = new TaiwanMahjongPlugin();
    plugin.onEnable();
    boolean ok = plugin.onCommand(player, new Command(), "mahjong", new String[]{"create", "測試牌桌"});
    if (!ok) throw new AssertionError("command returned false");
    long physicalBlocks = world.blocks.values().stream().filter(block -> block.type != Material.AIR).count();
    if (physicalBlocks != 18) throw new AssertionError("physical table should contain only the 3x3 fence/carpet structure: " + physicalBlocks);
    if (world.blocks.values().stream().anyMatch(block -> block.type == Material.OAK_SLAB)) {
      throw new AssertionError("seat slabs must not be generated");
    }
    Block tableBlock = world.getBlockAt(0, 64, 4);
    BlockBreakEvent protectedBreak = new BlockBreakEvent(tableBlock, player);
    plugin.onBlockBreak(protectedBreak);
    if (!protectedBreak.isCancelled()) throw new AssertionError("new tables must enable block protection by default");
    plugin.onCommand(player, new Command(), "mahjong", new String[]{"protection", "1", "off"});
    BlockBreakEvent unprotectedBreak = new BlockBreakEvent(tableBlock, player);
    plugin.onBlockBreak(unprotectedBreak);
    if (unprotectedBreak.isCancelled()) throw new AssertionError("disabled block protection must allow block breaking");
    if (world.entities.size() < 6) throw new AssertionError("3D/lobby entities missing: " + world.entities.size());
    long interactions = world.entities.stream().filter(e -> e instanceof Interaction).count();
    if (interactions < 1) throw new AssertionError("center interaction missing");
    if (!new File(folder, "tables.tsv").isFile()) throw new AssertionError("table persistence file missing");
    plugin.onCommand(player, new Command(), "mahjong", new String[]{"join", "1"});
    plugin.onCommand(player, new Command(), "mahjong", new String[]{"start"});
    long itemDisplays = world.entities.stream().filter(e -> e instanceof ItemDisplay && !((BaseEntity)e).removed).count();
    long activeInteractions = world.entities.stream().filter(e -> e instanceof Interaction && !((BaseEntity)e).removed).count();
    if (itemDisplays < 70) throw new AssertionError("3D game tiles missing: " + itemDisplays);
    if (activeInteractions < 10) throw new AssertionError("clickable hand interactions missing: " + activeInteractions);
    List<MockItemDisplay> privateHandFaces = player.shown.stream()
        .filter(e -> e instanceof MockItemDisplay)
        .map(e -> (MockItemDisplay)e)
        .toList();
    if (privateHandFaces.isEmpty()) throw new AssertionError("private hand faces were not shown to their owner");
    if (privateHandFaces.stream().anyMatch(display -> Math.abs(display.matrix.yawRadians) > 0.0001f)) {
      throw new AssertionError("seat 0 private hand faces must point toward their owner");
    }
    List<MockItemDisplay> hiddenHandBacks = player.hidden.stream()
        .filter(e -> e instanceof MockItemDisplay)
        .map(e -> (MockItemDisplay)e)
        .toList();
    if (hiddenHandBacks.isEmpty()) throw new AssertionError("public hand backs were not hidden from their owner");
    if (hiddenHandBacks.stream().anyMatch(display -> Math.abs(Math.abs(display.matrix.yawRadians) - Math.PI) > 0.0001)) {
      throw new AssertionError("seat 0 public hand backs must retain their table-facing rotation");
    }
    plugin.onDisable();
    TaiwanMahjongPlugin reloaded = new TaiwanMahjongPlugin();
    reloaded.onEnable();
    BlockBreakEvent persistedUnprotectedBreak = new BlockBreakEvent(tableBlock, player);
    reloaded.onBlockBreak(persistedUnprotectedBreak);
    if (persistedUnprotectedBreak.isCancelled()) throw new AssertionError("block protection setting must survive reload");
    MockBlock legacySeat = (MockBlock)world.getBlockAt(0, 64, 6);
    legacySeat.setType(Material.OAK_SLAB);
    reloaded.onCommand(player, new Command(), "mahjong", new String[]{"destroy", "1"});
    if (legacySeat.type != Material.AIR) throw new AssertionError("destroy must remove legacy seat slabs");
    reloaded.onDisable();
    System.out.println("PhysicalTableSmokeTest: physical blocks=" + physicalBlocks + ", live item displays=" + itemDisplays + ", live interactions=" + activeInteractions);
  }

  static final class MockServer implements Server {
    final PluginManager pm = new PluginManager(){ public void registerEvents(org.bukkit.event.Listener l, Plugin p){} public void disablePlugin(Plugin p){} };
    final BukkitScheduler scheduler = new BukkitScheduler(){
      public BukkitTask runTaskTimer(Plugin p,Runnable r,long d,long period){return ()->{};}
      public BukkitTask runTaskLater(Plugin p,Runnable r,long d){r.run();return ()->{};}
    };
    public PluginManager getPluginManager(){return pm;} public BukkitScheduler getScheduler(){return scheduler;} public String getMinecraftVersion(){return "26.2";}
  }

  static final class MockWorld implements World {
    final String name; final Map<String,MockBlock> blocks=new HashMap<>(); final List<Entity> entities=new ArrayList<>();
    MockWorld(String n){name=n;} public String getName(){return name;}
    public Entity spawnEntity(Location l, EntityType t){
      Entity e=switch(t){case ITEM_DISPLAY->new MockItemDisplay();case TEXT_DISPLAY->new MockTextDisplay();case INTERACTION->new MockInteraction();};
      entities.add(e); return e;
    }
    public Block getBlockAt(int x,int y,int z){return blocks.computeIfAbsent(x+","+y+","+z,k->new MockBlock(this,x,y,z));}
  }
  static final class MockBlock implements Block {
    final MockWorld w; final int x,y,z; Material type=Material.AIR;
    MockBlock(MockWorld w,int x,int y,int z){this.w=w;this.x=x;this.y=y;this.z=z;}
    public World getWorld(){return w;} public int getX(){return x;} public int getY(){return y;} public int getZ(){return z;}
    public Material getType(){return type;} public void setType(Material m){type=m;} public void setType(Material m,boolean p){type=m;}
  }
  static class BaseEntity implements Entity {
    final UUID id=UUID.randomUUID(); boolean removed;
    public UUID getUniqueId(){return id;} public void setPersistent(boolean v){} public void setVisibleByDefault(boolean v){} public void remove(){removed=true;} public boolean teleport(Location l){return true;}
  }
  static class BaseDisplay extends BaseEntity implements Display {
    public void setViewRange(float r){} public void setBrightness(Brightness b){} public void setBillboard(Billboard b){}
  }
  public static final class MockItemDisplay extends BaseDisplay implements ItemDisplay {
    Matrix4f matrix;
    public void setItemStack(ItemStack s){} public void setItemDisplayTransform(ItemDisplayTransform t){} public void setTransformationMatrix(Matrix4f m){matrix=m;}
  }
  static final class MockTextDisplay extends BaseDisplay implements TextDisplay {
    public void setText(String s){} public void setShadowed(boolean v){} public void setSeeThrough(boolean v){}
  }
  static final class MockInteraction extends BaseEntity implements Interaction {
    public void setInteractionWidth(float v){} public void setInteractionHeight(float v){} public void setResponsive(boolean v){}
  }
  static final class MockPlayer extends BaseEntity implements Player {
    final UUID id=UUID.randomUUID(); final MockWorld world; final List<String> messages=new ArrayList<>();
    final List<Entity> shown=new ArrayList<>(); final List<Entity> hidden=new ArrayList<>();
    MockPlayer(MockWorld w){world=w;} public UUID getUniqueId(){return id;} public String getName(){return "Tester";}
    public Location getLocation(){Location l=new Location(world,0,65,0);l.setYaw(0);return l;}
    public void showEntity(Plugin p,Entity e){shown.add(e);} public void hideEntity(Plugin p,Entity e){hidden.add(e);}
    public void sendMessage(String m){messages.add(m);} public boolean hasPermission(String p){return true;}
  }
}
