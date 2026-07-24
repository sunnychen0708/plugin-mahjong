package org.bukkit.entity;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;
public interface Player extends Entity, CommandSender {
  String getName(); UUID getUniqueId(); Location getLocation(); void showEntity(Plugin plugin,Entity entity); void hideEntity(Plugin plugin,Entity entity);
}
