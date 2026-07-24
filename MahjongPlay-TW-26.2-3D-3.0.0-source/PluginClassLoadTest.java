public final class PluginClassLoadTest {
  public static void main(String[] args) throws Exception {
    Class<?> c = Class.forName("com.mahjongplay.tw.TaiwanMahjongPlugin");
    Object o = c.getDeclaredConstructor().newInstance();
    if (o == null) throw new AssertionError();
    System.out.println("PluginClassLoadTest: " + c.getName() + " loaded");
  }
}
