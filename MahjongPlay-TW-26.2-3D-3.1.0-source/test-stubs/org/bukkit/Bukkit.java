package org.bukkit;
import java.util.*;
import org.bukkit.entity.Player;
public final class Bukkit {
  private static final Map<UUID,Player> PLAYERS=new HashMap<>();
  private static final Map<String,World> WORLDS=new HashMap<>();
  public static Player getPlayer(UUID id){return PLAYERS.get(id);}
  public static World getWorld(String name){return WORLDS.get(name);}
  public static void __setPlayer(UUID id,Player p){if(p==null)PLAYERS.remove(id);else PLAYERS.put(id,p);}
  public static void __setWorld(String name,World w){if(w==null)WORLDS.remove(name);else WORLDS.put(name,w);}
}
