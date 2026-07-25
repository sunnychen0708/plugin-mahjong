package org.bukkit;
import org.bukkit.block.Block;
public class Location implements Cloneable {
  private World world; private double x,y,z; private float yaw,pitch;
  public Location(World world,double x,double y,double z){this.world=world;this.x=x;this.y=y;this.z=z;}
  public World getWorld(){return world;} public double getX(){return x;} public double getY(){return y;} public double getZ(){return z;}
  public int getBlockX(){return (int)Math.floor(x);} public int getBlockY(){return (int)Math.floor(y);} public int getBlockZ(){return (int)Math.floor(z);}
  public float getYaw(){return yaw;} public void setYaw(float yaw){this.yaw=yaw;} public float getPitch(){return pitch;}
  public Location add(double dx,double dy,double dz){x+=dx;y+=dy;z+=dz;return this;}
  public Location clone(){Location l=new Location(world,x,y,z);l.yaw=yaw;l.pitch=pitch;return l;}
  public double distanceSquared(Location other){double dx=x-other.x,dy=y-other.y,dz=z-other.z;return dx*dx+dy*dy+dz*dz;}
  public Block getBlock(){return world.getBlockAt(getBlockX(),getBlockY(),getBlockZ());}
}
