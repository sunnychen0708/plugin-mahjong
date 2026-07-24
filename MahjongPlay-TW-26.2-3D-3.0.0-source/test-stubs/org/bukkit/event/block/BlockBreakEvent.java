package org.bukkit.event.block;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
public class BlockBreakEvent {
  private final Block block;
  private final Player player;
  private boolean cancelled;
  public BlockBreakEvent(Block block, Player player){this.block=block;this.player=player;}
  public Block getBlock(){return block;}
  public Player getPlayer(){return player;}
  public void setCancelled(boolean value){cancelled=value;}
  public boolean isCancelled(){return cancelled;}
}
