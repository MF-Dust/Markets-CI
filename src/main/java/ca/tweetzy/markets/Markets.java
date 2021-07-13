package ca.tweetzy.markets;

import ca.tweetzy.core.TweetyCore;
import ca.tweetzy.core.TweetyPlugin;
import ca.tweetzy.core.commands.CommandManager;
import ca.tweetzy.core.compatibility.ServerProject;
import ca.tweetzy.core.compatibility.ServerVersion;
import ca.tweetzy.core.compatibility.XMaterial;
import ca.tweetzy.core.configuration.Config;
import ca.tweetzy.core.database.DataMigrationManager;
import ca.tweetzy.core.database.DatabaseConnector;
import ca.tweetzy.core.database.MySQLConnector;
import ca.tweetzy.core.gui.GuiManager;
import ca.tweetzy.core.utils.Metrics;
import ca.tweetzy.markets.api.UpdateChecker;
import ca.tweetzy.markets.commands.*;
import ca.tweetzy.markets.database.DataManager;
import ca.tweetzy.markets.database.migrations._1_InitialMigration;
import ca.tweetzy.markets.economy.CurrencyBank;
import ca.tweetzy.markets.economy.EconomyManager;
import ca.tweetzy.markets.listeners.PlayerListeners;
import ca.tweetzy.markets.market.Market;
import ca.tweetzy.markets.market.MarketManager;
import ca.tweetzy.markets.market.MarketPlayerManager;
import ca.tweetzy.markets.request.Request;
import ca.tweetzy.markets.request.RequestManager;
import ca.tweetzy.markets.settings.LocaleSettings;
import ca.tweetzy.markets.settings.Settings;
import ca.tweetzy.markets.transaction.Payment;
import ca.tweetzy.markets.transaction.Transaction;
import ca.tweetzy.markets.transaction.TransactionManger;
import co.aikar.taskchain.BukkitTaskChainFactory;
import co.aikar.taskchain.TaskChain;
import co.aikar.taskchain.TaskChainFactory;
import lombok.Getter;
import org.bukkit.Bukkit;

import java.util.List;

/**
 * The current file has been created by Kiran Hart
 * Date Created: April 30 2021
 * Time Created: 3:28 p.m.
 * Usage of any code found within this class is prohibited unless given explicit permission otherwise
 */
public class Markets extends TweetyPlugin {

    private static TaskChainFactory taskChainFactory;

    @Getter
    private static Markets instance;

    @Getter
    private final Config data = new Config(this, "data.yml");

    @Getter
    private GuiManager guiManager;

    @Getter
    private CommandManager commandManager;

    @Getter
    private MarketManager marketManager;

    @Getter
    private TransactionManger transactionManger;

    @Getter
    private MarketPlayerManager marketPlayerManager;

    @Getter
    private RequestManager requestManager;

    @Getter
    private EconomyManager economyManager;

    @Getter
    private CurrencyBank currencyBank;

    @Getter
    private DataManager dataManager;

    @Getter
    protected DatabaseConnector databaseConnector;

    protected Metrics metrics;

    String IS_SONGODA_DOWNLOAD = "%%__SONGODA__%%";
    String SONGODA_NODE = "%%__SONGODA_NODE__%%";
    String TIMESTAMP = "%%__TIMESTAMP__%%";
    String USER = "%%__USER__%%";
    String USERNAME = "%%__USERNAME__%%";
    String RESOURCE = "%%__RESOURCE__%%";
    String NONCE = "%%__NONCE__%%";

    @Override
    public void onPluginLoad() {
        instance = this;
    }

    @Override
    public void onPluginEnable() {
        TweetyCore.registerPlugin(this, 101, XMaterial.CHEST.name());

        // Stop the plugin if the server version is not 1.8 or higher
        if (ServerVersion.isServerVersionAtOrBelow(ServerVersion.V1_7)) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Check server type
        if (ServerProject.isServer(ServerProject.CRAFTBUKKIT, ServerProject.GLOWSTONE, ServerProject.TACO, ServerProject.UNKNOWN)) {
            // I can't remember if spigot allows me to disable a plugin if its running a specific jar so just tell them AGAIN
            // that they're running a non-supported server project even though the plugin page will state it....
            getLogger().severe("You're running Markets on a non-supported server project. There is no guarantee anything will work.");
        }

        taskChainFactory = BukkitTaskChainFactory.create(this);

        // Setup the settings file
        Settings.setup();

        // Setup the new economy manager
        this.economyManager = new EconomyManager(this);

        // Setup the locale
        setLocale(Settings.LANG.getString());
        LocaleSettings.setup();

        // Load the data file
        this.data.load();

        // Setup the database if enabled
        if (Settings.DATABASE_USE.getBoolean()) {
            this.databaseConnector = new MySQLConnector(this, Settings.DATABASE_HOST.getString(), Settings.DATABASE_PORT.getInt(), Settings.DATABASE_NAME.getString(), Settings.DATABASE_USERNAME.getString(), Settings.DATABASE_PASSWORD.getString(), Settings.DATABASE_USE_SSL.getBoolean());
            this.dataManager = new DataManager(this.databaseConnector, this);
            DataMigrationManager dataMigrationManager = new DataMigrationManager(this.databaseConnector, this.dataManager,
                    new _1_InitialMigration()
            );
            dataMigrationManager.runMigrations();
        }

        // Managers
        this.guiManager = new GuiManager(this);
        this.commandManager = new CommandManager(this);
        this.marketManager = new MarketManager();
        this.marketPlayerManager = new MarketPlayerManager();
        this.transactionManger = new TransactionManger();
        this.requestManager = new RequestManager();
        this.currencyBank = new CurrencyBank();

        this.guiManager.init();
        this.marketManager.loadMarkets();
        this.transactionManger.loadTransactions();
        this.transactionManger.loadPayments();
        this.requestManager.loadRequests();
        this.marketManager.loadBlockedItems();
        this.currencyBank.loadBank();

        // Listeners
        Bukkit.getPluginManager().registerEvents(new PlayerListeners(), this);

        // Commands
        this.commandManager.addCommand(new CommandMarkets()).addSubCommands(
                new CommandCreate(),
                new CommandRemove(),
                new CommandAddCategory(),
                new CommandAddItem(),
                new CommandRequest(),
                new CommandPayments(),
                new CommandBank(),
                new CommandSet(),
                new CommandView(),
                new CommandList(),
                new CommandHelp(),
                new CommandSettings(),
                new CommandConfiscate(),
                new CommandReload(),
                new CommandsBlockItem()
        );

        // Perform the update check
        getServer().getScheduler().runTaskLaterAsynchronously(this, () -> new UpdateChecker(this, 92178, getConsole()).check(), 1L);
        if (Settings.AUTO_SAVE_ENABLED.getBoolean()) {
            getServer().getScheduler().runTaskTimerAsynchronously(this, this::saveData, 20L, (long) 20 * Settings.AUTO_SAVE_DELAY.getInt());
        }


        this.metrics = new Metrics(this, 7689);
    }

    @Override
    public void onPluginDisable() {
        saveData();
        instance = null;
    }

    private void saveData() {
        this.marketManager.saveMarkets(this.marketManager.getMarkets().toArray(new Market[0]));
        this.marketManager.saveBlockedItems();
        this.transactionManger.saveTransactions(this.transactionManger.getTransactions().toArray(new Transaction[0]));
        this.transactionManger.savePayments(this.transactionManger.getPayments().toArray(new Payment[0]));
        this.requestManager.saveRequests(this.requestManager.getRequests().toArray(new Request[0]));
        this.currencyBank.saveBank();
        getLogger().info("Market data has been automatically saved");
    }

    @Override
    public void onConfigReload() {
        Settings.setup();
        setLocale(Settings.LANG.getString());
        LocaleSettings.setup();
    }

    @Override
    public List<Config> getExtraConfig() {
        return null;
    }

    public static <T> TaskChain<T> newChain() {
        return taskChainFactory.newChain();
    }
}
