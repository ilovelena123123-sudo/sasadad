package pl.sklep;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SklepPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private final Map<UUID, Long> session = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playSeconds = new ConcurrentHashMap<>();
    private final Map<UUID, Long> spentSeconds = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> quantities = new HashMap<>();
    private final Map<UUID, Integer> stacks = new HashMap<>();
    private final Map<UUID, String> selectedCategory = new HashMap<>();
    private final NamespacedKey actionKey = new NamespacedKey(this, "action");
    private File dataFile;
    private YamlConfiguration data;
    private Object economy;

    @Override public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) try { dataFile.getParentFile().mkdirs(); dataFile.createNewFile(); } catch (Exception e) { getLogger().severe(e.getMessage()); }
        data = YamlConfiguration.loadConfiguration(dataFile);
        loadTimes();
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("sklep")).setExecutor(this);
        Objects.requireNonNull(getCommand("sklep-admin")).setExecutor(this);
        Objects.requireNonNull(getCommand("czasgry")).setExecutor(this);
        new BukkitRunnable() { public void run() {
            for (Player p : Bukkit.getOnlinePlayers()) playSeconds.merge(p.getUniqueId(), 1L, Long::sum);
            if (Bukkit.getCurrentTick() % 1200 == 0) saveTimes();
        }}.runTaskTimer(this, 20L, 20L);
        getLogger().info("Sklep zostal wlaczony.");
    }
    @Override public void onDisable() { saveTimes(); }

    private void loadTimes() {
        ConfigurationSection s = data.getConfigurationSection("time");
        if (s != null) for (String id : s.getKeys(false)) {
            UUID u; try { u = UUID.fromString(id); } catch (Exception ex) { continue; }
            playSeconds.put(u, s.getLong(id + ".earned", 0));
            spentSeconds.put(u, s.getLong(id + ".spent", 0));
        }
    }
    private void saveTimes() {
        for (UUID u : new HashSet<>(playSeconds.keySet())) {
            data.set("time." + u + ".earned", playSeconds.getOrDefault(u, 0L));
            data.set("time." + u + ".spent", spentSeconds.getOrDefault(u, 0L));
        }
        try { data.save(dataFile); } catch (Exception e) { getLogger().severe("Nie zapisano czasu: " + e.getMessage()); }
    }
    private long available(UUID u) { return Math.max(0, playSeconds.getOrDefault(u, 0L) - spentSeconds.getOrDefault(u, 0L)); }
    private String color(String s) { return s.replace('&', '§'); }
    private Component comp(String s) { return LegacyComponentSerializer.legacySection().deserialize(color(s)); }
    private ItemStack item(Material m, String name, String lore, String action) {
        ItemStack it = new ItemStack(m); ItemMeta meta = it.getItemMeta();
        meta.displayName(comp(name));
        if (lore != null && !lore.isBlank()) meta.lore(Arrays.stream(lore.split("\\n")).map(this::comp).toList());
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        it.setItemMeta(meta); return it;
    }
    private String action(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return "";
        return it.getItemMeta().getPersistentDataContainer().getOrDefault(actionKey, PersistentDataType.STRING, "");
    }
    private Inventory gui(String title, int size) { return Bukkit.createInventory(null, size, comp(title)); }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("czasgry")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Ta komenda jest dla gracza."); return true; }
            p.sendMessage(color("&fPosiadasz: &7" + formatTime(available(p.getUniqueId()))));
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("sklep-admin")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Komenda dla gracza."); return true; }
            if (!p.hasPermission("sklep.admin")) { p.sendMessage(color("&cBrak uprawnien.")); return true; }
            openAdmin(p); return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("sklep.admin")) { sender.sendMessage(color("&cBrak uprawnien.")); return true; }
            reloadConfig(); sender.sendMessage(color("&aPrzeladowano config.yml.")); return true;
        }
        if (!(sender instanceof Player p)) { sender.sendMessage("Komenda dla gracza."); return true; }
        openMain(p); return true;
    }

    private String formatTime(long sec) {
        long months = sec / 2592000; sec %= 2592000;
        long days = sec / 86400; sec %= 86400;
        long hours = sec / 3600; sec %= 3600;
        long mins = sec / 60; sec %= 60;
        List<String> out = new ArrayList<>();
        if (months > 0) out.add(months + " mies.");
        if (days > 0) out.add(days + " dni");
        if (hours > 0) out.add(hours + " godz.");
        if (mins > 0) out.add(mins + " min");
        if (sec > 0 || out.isEmpty()) out.add(sec + " sek");
        return String.join(" ", out);
    }
    private void openMain(Player p) {
        Inventory inv = gui("&8sᴋʟᴇᴘ", 27);
        inv.setItem(11, item(Material.EMERALD, "&aSklep za walute", "&7Kupuj i sprzedawaj za money", "main:money"));
        inv.setItem(15, item(Material.CLOCK, "&eSklep za czas", "&7Wymieniaj czas gry na przedmioty", "main:time"));
        p.openInventory(inv);
    }
    private void openMoney(Player p) {
        Inventory inv = gui(getConfig().getString("currency-title", "&8sᴋʟᴇᴘ"), 54);
        List<String> cats = data.getStringList("categories");
        int slot = 0;
        for (String cat : cats) {
            if (slot >= 45) break;
            ItemStack icon = data.getItemStack("category-items." + cat);
            if (icon == null) icon = new ItemStack(Material.CHEST);
            ItemStack show = icon.clone(); ItemMeta m = show.getItemMeta();
            m.displayName(comp("&e" + cat)); m.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "category:" + cat);
            show.setItemMeta(m); inv.setItem(slot++, show);
        }
        inv.setItem(49, item(Material.ARROW, "&cPowrot", "", "back"));
        p.openInventory(inv);
    }
    private void openCategory(Player p, String cat) {
        selectedCategory.put(p.getUniqueId(), cat);
        Inventory inv = gui("&8" + cat, 54);
        ConfigurationSection sec = data.getConfigurationSection("products." + cat);
        if (sec != null) {
            int slot = 0;
            for (String key : sec.getKeys(false)) {
                if (slot >= 45) break;
                ItemStack it = sec.getItemStack(key + ".item");
                if (it == null) continue;
                ItemStack display = it.clone(); ItemMeta meta = display.getItemMeta();
                List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
                lore.add(comp("&7Kupno: &a" + sec.getDouble(key + ".buy")));
                lore.add(comp("&7Sprzedaz: &c" + sec.getDouble(key + ".sell")));
                lore.add(comp("&ePPM: sprzedaj 1 | SHIFT+PPM: sprzedaj wszystko"));
                meta.lore(lore); meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "product:" + cat + ":" + key);
                display.setItemMeta(meta); inv.setItem(slot++, display);
            }
        }
        inv.setItem(45, item(Material.PAPER, "&fIlosc zakupu: " + quantities.getOrDefault(p.getUniqueId(), 1), "&7Kliknij, aby zmienic +1 / +16 / +64", "quantity"));
        inv.setItem(46, item(Material.CHEST, "&fStacki: " + stacks.getOrDefault(p.getUniqueId(), 1), "&7Kliknij, aby wybrac od 1 do 9", "stacks"));
        inv.setItem(49, item(Material.ARROW, "&cPowrot", "", "money"));
        p.openInventory(inv);
    }
    private void openTime(Player p) {
        Inventory inv = gui(getConfig().getString("time-title", "&8sᴋʟᴇᴘ ᴢᴀ ᴄᴢᴀs"), 54);
        ConfigurationSection sec = data.getConfigurationSection("time-products");
        if (sec != null) {
            int slot = 0;
            for (String key : sec.getKeys(false)) {
                if (slot >= 45) break;
                ItemStack it = sec.getItemStack(key + ".item"); if (it == null) continue;
                ItemStack display = it.clone(); ItemMeta meta = display.getItemMeta();
                List<Component> lore = new ArrayList<>(meta.lore() == null ? List.of() : meta.lore());
                lore.add(comp("&7Cena: &e" + formatTime(sec.getLong(key + ".price"))));
                lore.add(comp("&7Dostepny czas: &f" + formatTime(available(p.getUniqueId()))));
                meta.lore(lore); meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "timeproduct:" + key);
                display.setItemMeta(meta); inv.setItem(slot++, display);
            }
        }
        inv.setItem(49, item(Material.ARROW, "&cPowrot", "", "back"));
        p.openInventory(inv);
    }
    private void openAdmin(Player p) {
        Inventory inv = gui(getConfig().getString("admin-title", "&8ᴘᴀɴᴇʟ sᴋʟᴇᴘᴜ"), 27);
        inv.setItem(11, item(Material.EMERALD, "&aEdytuj sklep za walute", "&7Kategorie i produkty", "admin:money"));
        inv.setItem(15, item(Material.CLOCK, "&eEdytuj sklep za czas", "&7Produkty bez kategorii", "admin:time"));
        p.openInventory(inv);
    }
    private void openAdminMoney(Player p) {
        Inventory inv = gui("&8ᴀᴅᴍɪɴ: ᴋᴀᴛᴇɢᴏʀɪᴇ", 54);
        List<String> cats = data.getStringList("categories"); int slot = 0;
        for (String cat : cats) {
            if (slot >= 45) break;
            ItemStack icon = data.getItemStack("category-items." + cat);
            if (icon == null) icon = new ItemStack(Material.CHEST);
            ItemStack show = icon.clone(); ItemMeta m = show.getItemMeta(); m.displayName(comp("&e" + cat));
            m.setLore(List.of(comp("&7SHIFT+PPM: zarzadzaj"), comp("&cSHIFT+LPM: usun kategorie")));
            m.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "admincat:" + cat);
            show.setItemMeta(m); inv.setItem(slot++, show);
        }
        inv.setItem(49, item(Material.HOPPER, "&aDodaj kategorie", "&7Trzymaj nazwany przedmiot i kliknij SHIFT+PPM", "addcat"));
        inv.setItem(53, item(Material.ARROW, "&cPowrot", "", "adminback")); p.openInventory(inv);
    }
    private void openAdminCategory(Player p, String cat) {
        selectedCategory.put(p.getUniqueId(), cat);
        Inventory inv = gui("&8ᴀᴅᴍɪɴ: " + cat, 54);
        ConfigurationSection sec = data.getConfigurationSection("products." + cat);
        if (sec != null) {
            int slot = 0;
            for (String key : sec.getKeys(false)) {
                ItemStack it = sec.getItemStack(key + ".item");
                if (it != null && slot < 45) { ItemStack display = it.clone(); ItemMeta m = display.getItemMeta();
                    m.setLore(List.of(comp("&7Kupno: " + sec.getDouble(key + ".buy")), comp("&7Sprzedaz: " + sec.getDouble(key + ".sell")), comp("&eSHIFT+LPM: usun")));
                    m.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "adminproduct:" + key); display.setItemMeta(m); inv.setItem(slot++, display); }
            }
        }
        inv.setItem(49, item(Material.HOPPER, "&aDodaj produkt", "&7Trzymaj przedmiot i kliknij SHIFT+PPM", "addproduct"));
        inv.setItem(53, item(Material.ARROW, "&cPowrot", "", "adminmoney")); p.openInventory(inv);
    }
    private void openAdminTime(Player p) {
        Inventory inv = gui("&8ᴀᴅᴍɪɴ: sᴋʟᴇᴘ ᴢᴀ ᴄᴢᴀs", 54);
        ConfigurationSection sec = data.getConfigurationSection("time-products"); int slot = 0;
        if (sec != null) for (String key : sec.getKeys(false)) {
            ItemStack it = sec.getItemStack(key + ".item");
            if (it != null && slot < 45) { ItemStack d = it.clone(); ItemMeta m = d.getItemMeta();
                m.setLore(List.of(comp("&7Cena: " + formatTime(sec.getLong(key + ".price"))), comp("&eSHIFT+LPM: usun")));
                m.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "admintimeproduct:" + key); d.setItemMeta(m); inv.setItem(slot++, d); }
        }
        inv.setItem(49, item(Material.HOPPER, "&aDodaj przedmiot za czas", "&7Trzymaj przedmiot i kliknij SHIFT+PPM", "addtimeproduct"));
        inv.setItem(53, item(Material.ARROW, "&cPowrot", "", "adminback")); p.openInventory(inv);
    }

    @EventHandler public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = LegacyComponentSerializer.legacySection().serialize(e.getView().title());
        if (!title.contains("sᴋʟᴇᴘ") && !title.contains("ᴀᴅᴍɪɴ")) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
        e.setCancelled(true);
        ItemStack clicked = e.getCurrentItem(); String a = action(clicked);
        if (a.isEmpty()) return;
        if (a.startsWith("main:")) { if (a.endsWith("money")) openMoney(p); else openTime(p); return; }
        if (a.equals("back")) { openMain(p); return; }
        if (a.equals("money")) { openMoney(p); return; }
        if (a.startsWith("category:")) { openCategory(p, a.substring(9)); return; }
        if (a.equals("quantity")) { int[] q={1,16,64}; int n=quantities.getOrDefault(p.getUniqueId(),1); quantities.put(p.getUniqueId(), q[(Arrays.binarySearch(q,n)+1+q.length)%q.length]); openCategory(p, selectedCategory.getOrDefault(p.getUniqueId(),"")); return; }
        if (a.equals("stacks")) { stacks.put(p.getUniqueId(), stacks.getOrDefault(p.getUniqueId(),1)%9+1); openCategory(p, selectedCategory.getOrDefault(p.getUniqueId(),"")); return; }
        if (a.startsWith("product:")) { handleProduct(p, a, e.isRightClick(), e.isShiftClick()); return; }
        if (a.startsWith("timeproduct:")) { buyTime(p, a.substring(12)); return; }
        if (a.startsWith("admin:")) { if (a.endsWith("money")) openAdminMoney(p); else openAdminTime(p); return; }
        if (a.equals("adminback")) { openAdmin(p); return; }
        if (a.equals("adminmoney")) { openAdminMoney(p); return; }
        if (a.equals("addcat")) { addCategory(p); return; }
        if (a.startsWith("admincat:")) {
            String cat=a.substring(9);
            if (e.isShiftClick() && e.isRightClick()) openAdminCategory(p,cat);
            else if (e.isShiftClick() && e.isLeftClick()) { data.getStringList("categories").remove(cat); List<String> list=data.getStringList("categories"); list.remove(cat); data.set("categories",list); data.set("category-items."+cat,null); data.set("products."+cat,null); saveData(); openAdminMoney(p); }
            return;
        }
        if (a.equals("addproduct")) { addProduct(p); return; }
        if (a.startsWith("adminproduct:") && e.isShiftClick() && e.isLeftClick()) {
            String cat=selectedCategory.get(p.getUniqueId()); data.set("products."+cat+"."+a.substring(13),null); saveData(); openAdminCategory(p,cat); return;
        }
        if (a.equals("addtimeproduct")) { addTimeProduct(p); return; }
        if (a.startsWith("admintimeproduct:") && e.isShiftClick() && e.isLeftClick()) {
            data.set("time-products."+a.substring(17),null); saveData(); openAdminTime(p);
        }
    }
    private void handleProduct(Player p, String a, boolean right, boolean shift) {
        String[] parts=a.split(":",3); String cat=parts[1], key=parts[2];
        String path="products."+cat+"."+key;
        if (right) {
            int amount=shift ? count(p, data.getItemStack(path+".item")) : 1;
            if (amount <= 0) return;
            double price=data.getDouble(path+".sell")*amount;
            if (price <= 0 || !takeMoney(p, -price)) { p.sendMessage(color("&cNie udalo sie sprzedac przedmiotu.")); return; }
            removeItems(p, data.getItemStack(path+".item"), amount);
            p.sendMessage(color("&aSprzedano za "+price+" money."));
        } else {
            int amount=quantities.getOrDefault(p.getUniqueId(),1)*stacks.getOrDefault(p.getUniqueId(),1);
            ItemStack template=data.getItemStack(path+".item"); if(template==null)return;
            amount=Math.min(amount, template.getMaxStackSize()*9);
            double price=data.getDouble(path+".buy")*amount;
            if (!takeMoney(p, price)) { p.sendMessage(color("&cNie masz wystarczajaco money.")); return; }
            give(p, template, amount);
        }
        openCategory(p,cat);
    }
    private int count(Player p, ItemStack template) { if(template==null)return 0; int n=0; for(ItemStack it:p.getInventory().getStorageContents()) if(it!=null && it.isSimilar(template)) n+=it.getAmount(); return n; }
    private void removeItems(Player p, ItemStack template, int amount) {
        if(template==null)return; for(int i=0;i<p.getInventory().getSize() && amount>0;i++){ ItemStack it=p.getInventory().getItem(i); if(it!=null && it.isSimilar(template)){int n=Math.min(amount,it.getAmount()); it.setAmount(it.getAmount()-n); amount-=n;}}
    }
    private void give(Player p, ItemStack template, int amount) {
        while(amount>0){int n=Math.min(amount,template.getMaxStackSize()); ItemStack give=template.clone(); give.setAmount(n); Map<Integer,ItemStack> overflow=p.getInventory().addItem(give); overflow.values().forEach(x->p.getWorld().dropItemNaturally(p.getLocation(),x)); amount-=n;}
    }
    private boolean takeMoney(Player p,double amount) {
        try {
            if(economy==null){var reg=Bukkit.getServicesManager().getRegistration(Class.forName("net.milkbowl.vault.economy.Economy")); if(reg==null)return false; economy=reg.getProvider();}
            Method get=economy.getClass().getMethod("getBalance", OfflinePlayer.class);
            if(amount>0 && ((Number)get.invoke(economy,p)).doubleValue()<amount)return false;
            Method m=economy.getClass().getMethod(amount>=0?"withdrawPlayer":"depositPlayer", OfflinePlayer.class,double.class);
            Object response=m.invoke(economy,p,Math.abs(amount));
            return (boolean)response.getClass().getMethod("transactionSuccess").invoke(response);
        } catch(Exception ex){ p.sendMessage(color("&cBrak Vault lub pluginu ekonomii (np. EssentialsX).")); return false; }
    }
    private void buyTime(Player p,String key) {
        String path="time-products."+key; long price=data.getLong(path+".price"); if(available(p.getUniqueId())<price){p.sendMessage(color("&cMasz za malo czasu."));return;}
        ItemStack it=data.getItemStack(path+".item"); if(it==null)return;
        spentSeconds.merge(p.getUniqueId(),price,Long::sum); give(p,it,it.getAmount()); p.sendMessage(color("&aKupiono za "+formatTime(price))); openTime(p);
    }
    private void addCategory(Player p) {
        ItemStack held=p.getInventory().getItemInMainHand();
        if(held.getType().isAir() || !held.hasItemMeta() || !held.getItemMeta().hasDisplayName()){p.sendMessage(color("&cTrzymaj nazwany przedmiot."));return;}
        String name=LegacyComponentSerializer.legacySection().serialize(held.getItemMeta().displayName()).replace("§","").replaceAll("[^a-zA-Z0-9_-]","_");
        List<String> cats=data.getStringList("categories"); if(cats.contains(name)){p.sendMessage(color("&cTaka kategoria juz istnieje."));return;}
        cats.add(name); data.set("categories",cats); data.set("category-items."+name,held.clone()); saveData(); openAdminMoney(p);
    }
    private void addProduct(Player p) {
        String cat=selectedCategory.get(p.getUniqueId()); if(cat==null)return;
        ItemStack held=p.getInventory().getItemInMainHand(); if(held.getType().isAir()){p.sendMessage(color("&cTrzymaj przedmiot w rece."));return;}
        String key=UUID.randomUUID().toString().substring(0,8);
        data.set("products."+cat+"."+key+".item",held.clone()); data.set("products."+cat+"."+key+".buy",0.0); data.set("products."+cat+"."+key+".sell",0.0);
        saveData(); p.sendMessage(color("&eDodano produkt. Ustaw ceny w data.yml: buy i sell.")); openAdminCategory(p,cat);
    }
    private void addTimeProduct(Player p) {
        ItemStack held=p.getInventory().getItemInMainHand(); if(held.getType().isAir())return;
        String key=UUID.randomUUID().toString().substring(0,8);
        data.set("time-products."+key+".item",held.clone()); data.set("time-products."+key+".price",3600L);
        saveData(); p.sendMessage(color("&eDodano produkt za czas. Zmien cene w data.yml (sekundy).")); openAdminTime(p);
    }
    private void saveData(){try{data.save(dataFile);}catch(Exception ex){getLogger().severe(ex.getMessage());}}
}
