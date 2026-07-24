package org.bukkit.plugin.java;
import java.io.File;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
public class JavaPlugin implements Plugin {
  private static Server server; private static PluginCommand command; private static File dataFolder=new File(".");
  public static void __setServer(Server value){server=value;} public static void __setCommand(PluginCommand value){command=value;} public static void __setDataFolder(File value){dataFolder=value;}
  public void onEnable(){} public void onDisable(){} public PluginCommand getCommand(String name){return command;}
  public Server getServer(){return server;} public File getDataFolder(){return dataFolder;}
  public Logger getLogger(){return Logger.getLogger(getClass().getName());}
}
