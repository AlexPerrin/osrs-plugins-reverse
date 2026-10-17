// Headless RSProx launcher for the desktop A/B client (docs/multi-client.md).
//
// Starts RSProx's ProxyService without its GUI, selects the "OpenRune Test" proxy target and
// launches one RuneLite session through it. It refuses any other target: the desktop test client
// must only ever reach our local OpenRune server, never Jagex. Exits when the client exits.
//
// Built by tools/multiclient/desktop-build.sh against the RSProx runtime classpath; run on Windows
// by tools/multiclient/desktop.sh with -Duser.home=C:\osrs-test\home (JAVA_TOOL_OPTIONS).

import io.netty.buffer.ByteBufAllocator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.rsprox.cache.api.CacheProvider;
import net.rsprox.proxy.ProxyService;
import net.rsprox.proxy.binary.BinaryHeader;
import net.rsprox.proxy.target.ProxyTarget;
import net.rsprox.proxy.target.ProxyTargetConfig;
import net.rsprox.proxy.util.ProgressCallback;
import net.rsprox.shared.SessionMonitor;
import net.rsprox.shared.property.RootProperty;

public final class OsrsTestLauncher
{
	static final String TARGET = "OpenRune Test";
	static final String JAV_CONFIG_PREFIX = "http://127.0.0.1:";

	static void log(String s)
	{
		System.out.println("[osrs-test] " + s);
		System.out.flush();
	}

	static void die(String s)
	{
		log("REFUSING: " + s);
		System.exit(2);
	}

	public static void main(String[] args) throws Exception
	{
		String home = System.getProperty("user.home");
		log("user.home=" + home);
		if (!home.replace('\\', '/').toLowerCase().startsWith("c:/osrs-test/"))
		{
			die("user.home is not under C:\\osrs-test (isolation not active)");
		}
		if (System.getenv("RSPS_JAVCONFIG_URL") != null || System.getenv("RSPS_RSA") != null)
		{
			die("RSPS_* overrides are set");
		}
		for (String v : new String[]{"JX_ACCESS_TOKEN", "JX_SESSION_ID", "JX_CHARACTER_ID", "JX_REFRESH_TOKEN", "JX_DISPLAY_NAME"})
		{
			if (System.getenv(v) != null)
			{
				die("Jagex launcher variable " + v + " is set");
			}
		}

		ProxyService service = new ProxyService(ByteBufAllocator.DEFAULT);
		service.start(null, null, new ProgressCallback()
		{
			@Override
			public void update(double p, String a, String sub, String t)
			{
				log(String.format("start %3.0f%% %s", p * 100, sub));
			}

			@Override
			public void update(double p, String a, String sub)
			{
				update(p, a, sub, null);
			}
		});

		List<ProxyTargetConfig> targets = service.getProxyTargets();
		int index = -1;
		for (int i = 0; i < targets.size(); i++)
		{
			ProxyTargetConfig c = targets.get(i);
			log("target " + i + ": id=" + c.getId() + " name=" + c.getName() + " jav_config=" + c.getJavConfigUrl());
			if (TARGET.equals(c.getName()))
			{
				if (index != -1)
				{
					die("more than one target named " + TARGET);
				}
				index = i;
			}
		}
		if (index < 0)
		{
			die("no proxy target named " + TARGET + " in ~/.rsprox/proxy-targets.yaml");
		}
		ProxyTargetConfig cfg = targets.get(index);
		if (cfg.getId() == 0 || !cfg.getJavConfigUrl().startsWith(JAV_CONFIG_PREFIX) || cfg.getModulus() == null)
		{
			die("target " + TARGET + " is not a local target with its own modulus: " + cfg);
		}
		service.setSelectedProxyTarget(index);
		if (service.getSelectedProxyTarget() != index)
		{
			die("could not select " + TARGET);
		}

		int port = service.allocatePort();
		ProxyTarget target = service.initializeHttpServer(port);
		if (target.getConfig() != cfg)
		{
			die("initialized target is not " + TARGET);
		}
		log("proxy port " + port + ", jav_config http://127.0.0.1:" + target.getHttpPort() + " -> " + cfg.getJavConfigUrl());
		service.launchRuneLiteClient(new Monitor(), null, port, target);
		log("RuneLite launched");

		// Exit with the client: RSProx's own threads would keep us alive otherwise.
		long idleSince = -1;
		while (true)
		{
			TimeUnit.SECONDS.sleep(5);
			boolean alive = ProcessHandle.current().descendants().anyMatch(ProcessHandle::isAlive);
			if (alive)
			{
				idleSince = -1;
				continue;
			}
			if (idleSince < 0)
			{
				idleSince = System.nanoTime();
			}
			else if (System.nanoTime() - idleSince > TimeUnit.SECONDS.toNanos(10))
			{
				log("client exited; shutting down");
				service.safeShutdown();
				System.exit(0);
			}
		}
	}

	static final class Monitor implements SessionMonitor<BinaryHeader>
	{
		@Override
		public SessionMonitor<BinaryHeader> forSession(BinaryHeader header)
		{
			return this;
		}

		@Override
		public void onLogin(BinaryHeader header)
		{
			log("login: " + header);
		}

		@Override
		public void onLogout(BinaryHeader header)
		{
			log("logout");
		}

		@Override
		public void onCacheUpdate(CacheProvider cacheProvider)
		{
		}

		@Override
		public void onIncomingBytesPerSecondUpdate(long b)
		{
		}

		@Override
		public void onOutgoingBytesPerSecondUpdate(long b)
		{
		}

		@Override
		public void onNameUpdate(String name)
		{
			log("name: " + name);
		}

		@Override
		public void onUserInformationUpdate(long userId, long userHash)
		{
		}

		@Override
		public void onTranscribe(int cycle, RootProperty property)
		{
		}
	}
}
