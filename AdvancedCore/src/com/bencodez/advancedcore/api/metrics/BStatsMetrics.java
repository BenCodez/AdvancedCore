package com.bencodez.advancedcore.api.metrics;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import javax.net.ssl.HttpsURLConnection;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * bStats collects some data for plugin authors.
 *
 * Check out https://bStats.org/ to learn more about bStats!
 */
public class BStatsMetrics {

	/**
	 * Represents a custom advanced map chart.
	 */
	public static abstract class AdvancedMapChart extends CustomChart {

		/**
		 * Class constructor.
		 *
		 * @param chartId The id of the chart.
		 */
		public AdvancedMapChart(String chartId) {
			super(chartId);
		}

		@Override
		protected JsonObject getChartData() {
			JsonObject data = new JsonObject();
			JsonObject values = new JsonObject();
			HashMap<Country, Integer> map = getValues(new HashMap<Country, Integer>());
			if (map == null || map.isEmpty()) {
				// Null = skip the chart
				return null;
			}
			boolean allSkipped = true;
			for (Map.Entry<Country, Integer> entry : map.entrySet()) {
				if (entry.getValue() == 0) {
					continue; // Skip this invalid
				}
				allSkipped = false;
				values.addProperty(entry.getKey().getCountryIsoTag(), entry.getValue());
			}
			if (allSkipped) {
				// Null = skip the chart
				return null;
			}
			data.add("values", values);
			return data;
		}

		/**
		 * Gets the value of the chart.
		 *
		 * @param valueMap Just an empty map. The only reason it exists is to make your
		 *                 life easier. You don't have to create a map yourself!
		 * @return The value of the chart.
		 */
		public abstract HashMap<Country, Integer> getValues(HashMap<Country, Integer> valueMap);

	}

	/**
	 * Represents a custom advanced pie.
	 */
	public static abstract class AdvancedPie extends CustomChart {

		/**
		 * Class constructor.
		 *
		 * @param chartId The id of the chart.
		 */
		public AdvancedPie(String chartId) {
			super(chartId);
		}

		@Override
		protected JsonObject getChartData() {
			JsonObject data = new JsonObject();
			JsonObject values = new JsonObject();
			HashMap<String, Integer> map = getValues(new HashMap<String, Integer>());
			if (map == null || map.isEmpty()) {
				// Null = skip the chart
				return null;
			}
			boolean allSkipped = true;
			for (Map.Entry<String, Integer> entry : map.entrySet()) {
				if (entry.getValue() == 0) {
					continue; // Skip this invalid
				}
				allSkipped = false;
				values.addProperty(entry.getKey(), entry.getValue());
			}
			if (allSkipped) {
				// Null = skip the chart
				return null;
			}
			data.add("values", values);
			return data;
		}

		/**
		 * Gets the values of the pie.
		 *
		 * @param valueMap Just an empty map. The only reason it exists is to make your
		 *                 life easier. You don't have to create a map yourself!
		 * @return The values of the pie.
		 */
		public abstract HashMap<String, Integer> getValues(HashMap<String, Integer> valueMap);
	}

	/**
	 * A enum which is used for custom maps.
	 */
	public enum Country {

		AFGHANISTAN("AF", "Afghanistan"),

		ÅLAND_ISLANDS("AX", "Åland Islands"), ALBANIA("AL", "Albania"), ALGERIA("DZ", "Algeria"),
		AMERICAN_SAMOA("AS", "American Samoa"), ANDORRA("AD", "Andorra"), ANGOLA("AO", "Angola"),
		ANGUILLA("AI", "Anguilla"), ANTARCTICA("AQ", "Antarctica"), ANTIGUA_AND_BARBUDA("AG", "Antigua and Barbuda"),
		ARGENTINA("AR", "Argentina"), ARMENIA("AM", "Armenia"), ARUBA("AW", "Aruba"), AUSTRALIA("AU", "Australia"),
		AUSTRIA("AT", "Austria"),
		/**
		 * bStats will use the country of the server.
		 */
		AUTO_DETECT("AUTO", "Auto Detected"), AZERBAIJAN("AZ", "Azerbaijan"), BAHAMAS("BS", "Bahamas"),
		BAHRAIN("BH", "Bahrain"), BANGLADESH("BD", "Bangladesh"), BARBADOS("BB", "Barbados"), BELARUS("BY", "Belarus"),
		BELGIUM("BE", "Belgium"), BELIZE("BZ", "Belize"), BENIN("BJ", "Benin"), BERMUDA("BM", "Bermuda"),
		BHUTAN("BT", "Bhutan"), BOLIVIA("BO", "Bolivia"),
		BONAIRE_SINT_EUSTATIUS_AND_SABA("BQ", "Bonaire, Sint Eustatius and Saba"),
		BOSNIA_AND_HERZEGOVINA("BA", "Bosnia and Herzegovina"), BOTSWANA("BW", "Botswana"),
		BOUVET_ISLAND("BV", "Bouvet Island"), BRAZIL("BR", "Brazil"),
		BRITISH_INDIAN_OCEAN_TERRITORY("IO", "British Indian Ocean Territory"),
		BRITISH_VIRGIN_ISLANDS("VG", "British Virgin Islands"), BRUNEI("BN", "Brunei"), BULGARIA("BG", "Bulgaria"),
		BURKINA_FASO("BF", "Burkina Faso"), BURUNDI("BI", "Burundi"), CAMBODIA("KH", "Cambodia"),
		CAMEROON("CM", "Cameroon"), CANADA("CA", "Canada"), CAPE_VERDE("CV", "Cape Verde"),
		CAYMAN_ISLANDS("KY", "Cayman Islands"), CENTRAL_AFRICAN_REPUBLIC("CF", "Central African Republic"),
		CHAD("TD", "Chad"), CHILE("CL", "Chile"), CHINA("CN", "China"), CHRISTMAS_ISLAND("CX", "Christmas Island"),
		COCOS_ISLANDS("CC", "Cocos Islands"), COLOMBIA("CO", "Colombia"), COMOROS("KM", "Comoros"),
		CONGO("CG", "Congo"), COOK_ISLANDS("CK", "Cook Islands"), COSTA_RICA("CR", "Costa Rica"),
		CÔTE_D_IVOIRE("CI", "Côte d'Ivoire"), CROATIA("HR", "Croatia"), CUBA("CU", "Cuba"), CURAÇAO("CW", "Curaçao"),
		CYPRUS("CY", "Cyprus"), CZECH_REPUBLIC("CZ", "Czech Republic"), DENMARK("DK", "Denmark"),
		DJIBOUTI("DJ", "Djibouti"), DOMINICA("DM", "Dominica"), DOMINICAN_REPUBLIC("DO", "Dominican Republic"),
		ECUADOR("EC", "Ecuador"), EGYPT("EG", "Egypt"), EL_SALVADOR("SV", "El Salvador"),
		EQUATORIAL_GUINEA("GQ", "Equatorial Guinea"), ERITREA("ER", "Eritrea"), ESTONIA("EE", "Estonia"),
		ETHIOPIA("ET", "Ethiopia"), FALKLAND_ISLANDS("FK", "Falkland Islands"), FAROE_ISLANDS("FO", "Faroe Islands"),
		FIJI("FJ", "Fiji"), FINLAND("FI", "Finland"), FRANCE("FR", "France"), FRENCH_GUIANA("GF", "French Guiana"),
		FRENCH_POLYNESIA("PF", "French Polynesia"), FRENCH_SOUTHERN_TERRITORIES("TF", "French Southern Territories"),
		GABON("GA", "Gabon"), GAMBIA("GM", "Gambia"), GEORGIA("GE", "Georgia"), GERMANY("DE", "Germany"),
		GHANA("GH", "Ghana"), GIBRALTAR("GI", "Gibraltar"), GREECE("GR", "Greece"), GREENLAND("GL", "Greenland"),
		GRENADA("GD", "Grenada"), GUADELOUPE("GP", "Guadeloupe"), GUAM("GU", "Guam"), GUATEMALA("GT", "Guatemala"),
		GUERNSEY("GG", "Guernsey"), GUINEA("GN", "Guinea"), GUINEA_BISSAU("GW", "Guinea-Bissau"),
		GUYANA("GY", "Guyana"), HAITI("HT", "Haiti"),
		HEARD_ISLAND_AND_MCDONALD_ISLANDS("HM", "Heard Island And McDonald Islands"), HONDURAS("HN", "Honduras"),
		HONG_KONG("HK", "Hong Kong"), HUNGARY("HU", "Hungary"), ICELAND("IS", "Iceland"), INDIA("IN", "India"),
		INDONESIA("ID", "Indonesia"), IRAN("IR", "Iran"), IRAQ("IQ", "Iraq"), IRELAND("IE", "Ireland"),
		ISLE_OF_MAN("IM", "Isle Of Man"), ISRAEL("IL", "Israel"), ITALY("IT", "Italy"), JAMAICA("JM", "Jamaica"),
		JAPAN("JP", "Japan"), JERSEY("JE", "Jersey"), JORDAN("JO", "Jordan"), KAZAKHSTAN("KZ", "Kazakhstan"),
		KENYA("KE", "Kenya"), KIRIBATI("KI", "Kiribati"), KUWAIT("KW", "Kuwait"), KYRGYZSTAN("KG", "Kyrgyzstan"),
		LAOS("LA", "Laos"), LATVIA("LV", "Latvia"), LEBANON("LB", "Lebanon"), LESOTHO("LS", "Lesotho"),
		LIBERIA("LR", "Liberia"), LIBYA("LY", "Libya"), LIECHTENSTEIN("LI", "Liechtenstein"),
		LITHUANIA("LT", "Lithuania"), LUXEMBOURG("LU", "Luxembourg"), MACAO("MO", "Macao"),
		MACEDONIA("MK", "Macedonia"), MADAGASCAR("MG", "Madagascar"), MALAWI("MW", "Malawi"),
		MALAYSIA("MY", "Malaysia"), MALDIVES("MV", "Maldives"), MALI("ML", "Mali"), MALTA("MT", "Malta"),
		MARSHALL_ISLANDS("MH", "Marshall Islands"), MARTINIQUE("MQ", "Martinique"), MAURITANIA("MR", "Mauritania"),
		MAURITIUS("MU", "Mauritius"), MAYOTTE("YT", "Mayotte"), MEXICO("MX", "Mexico"), MICRONESIA("FM", "Micronesia"),
		MOLDOVA("MD", "Moldova"), MONACO("MC", "Monaco"), MONGOLIA("MN", "Mongolia"), MONTENEGRO("ME", "Montenegro"),
		MONTSERRAT("MS", "Montserrat"), MOROCCO("MA", "Morocco"), MOZAMBIQUE("MZ", "Mozambique"),
		MYANMAR("MM", "Myanmar"), NAMIBIA("NA", "Namibia"), NAURU("NR", "Nauru"), NEPAL("NP", "Nepal"),
		NETHERLANDS("NL", "Netherlands"), NETHERLANDS_ANTILLES("AN", "Netherlands Antilles"),
		NEW_CALEDONIA("NC", "New Caledonia"), NEW_ZEALAND("NZ", "New Zealand"), NICARAGUA("NI", "Nicaragua"),
		NIGER("NE", "Niger"), NIGERIA("NG", "Nigeria"), NIUE("NU", "Niue"), NORFOLK_ISLAND("NF", "Norfolk Island"),
		NORTH_KOREA("KP", "North Korea"), NORTHERN_MARIANA_ISLANDS("MP", "Northern Mariana Islands"),
		NORWAY("NO", "Norway"), OMAN("OM", "Oman"), PAKISTAN("PK", "Pakistan"), PALAU("PW", "Palau"),
		PALESTINE("PS", "Palestine"), PANAMA("PA", "Panama"), PAPUA_NEW_GUINEA("PG", "Papua New Guinea"),
		PARAGUAY("PY", "Paraguay"), PERU("PE", "Peru"), PHILIPPINES("PH", "Philippines"), PITCAIRN("PN", "Pitcairn"),
		POLAND("PL", "Poland"), PORTUGAL("PT", "Portugal"), PUERTO_RICO("PR", "Puerto Rico"), QATAR("QA", "Qatar"),
		REUNION("RE", "Reunion"), ROMANIA("RO", "Romania"), RUSSIA("RU", "Russia"), RWANDA("RW", "Rwanda"),
		SAINT_BARTHÉLEMY("BL", "Saint Barthélemy"), SAINT_HELENA("SH", "Saint Helena"),
		SAINT_KITTS_AND_NEVIS("KN", "Saint Kitts And Nevis"), SAINT_LUCIA("LC", "Saint Lucia"),
		SAINT_MARTIN("MF", "Saint Martin"), SAINT_PIERRE_AND_MIQUELON("PM", "Saint Pierre And Miquelon"),
		SAINT_VINCENT_AND_THE_GRENADINES("VC", "Saint Vincent And The Grenadines"), SAMOA("WS", "Samoa"),
		SAN_MARINO("SM", "San Marino"), SAO_TOME_AND_PRINCIPE("ST", "Sao Tome And Principe"),
		SAUDI_ARABIA("SA", "Saudi Arabia"), SENEGAL("SN", "Senegal"), SERBIA("RS", "Serbia"),
		SEYCHELLES("SC", "Seychelles"), SIERRA_LEONE("SL", "Sierra Leone"), SINGAPORE("SG", "Singapore"),
		SINT_MAARTEN_DUTCH_PART("SX", "Sint Maarten (Dutch part)"), SLOVAKIA("SK", "Slovakia"),
		SLOVENIA("SI", "Slovenia"), SOLOMON_ISLANDS("SB", "Solomon Islands"), SOMALIA("SO", "Somalia"),
		SOUTH_AFRICA("ZA", "South Africa"),
		SOUTH_GEORGIA_AND_THE_SOUTH_SANDWICH_ISLANDS("GS", "South Georgia And The South Sandwich Islands"),
		SOUTH_KOREA("KR", "South Korea"), SOUTH_SUDAN("SS", "South Sudan"), SPAIN("ES", "Spain"),
		SRI_LANKA("LK", "Sri Lanka"), SUDAN("SD", "Sudan"), SURINAME("SR", "Suriname"),
		SVALBARD_AND_JAN_MAYEN("SJ", "Svalbard And Jan Mayen"), SWAZILAND("SZ", "Swaziland"), SWEDEN("SE", "Sweden"),
		SWITZERLAND("CH", "Switzerland"), SYRIA("SY", "Syria"), TAIWAN("TW", "Taiwan"), TAJIKISTAN("TJ", "Tajikistan"),
		TANZANIA("TZ", "Tanzania"), THAILAND("TH", "Thailand"),
		THE_DEMOCRATIC_REPUBLIC_OF_CONGO("CD", "The Democratic Republic Of Congo"), TIMOR_LESTE("TL", "Timor-Leste"),
		TOGO("TG", "Togo"), TOKELAU("TK", "Tokelau"), TONGA("TO", "Tonga"),
		TRINIDAD_AND_TOBAGO("TT", "Trinidad and Tobago"), TUNISIA("TN", "Tunisia"), TURKEY("TR", "Turkey"),
		TURKMENISTAN("TM", "Turkmenistan"), TURKS_AND_CAICOS_ISLANDS("TC", "Turks And Caicos Islands"),
		TUVALU("TV", "Tuvalu"), U_S__VIRGIN_ISLANDS("VI", "U.S. Virgin Islands"), UGANDA("UG", "Uganda"),
		UKRAINE("UA", "Ukraine"), UNITED_ARAB_EMIRATES("AE", "United Arab Emirates"),
		UNITED_KINGDOM("GB", "United Kingdom"), UNITED_STATES("US", "United States"),
		UNITED_STATES_MINOR_OUTLYING_ISLANDS("UM", "United States Minor Outlying Islands"), URUGUAY("UY", "Uruguay"),
		UZBEKISTAN("UZ", "Uzbekistan"), VANUATU("VU", "Vanuatu"), VATICAN("VA", "Vatican"),
		VENEZUELA("VE", "Venezuela"), VIETNAM("VN", "Vietnam"), WALLIS_AND_FUTUNA("WF", "Wallis And Futuna"),
		WESTERN_SAHARA("EH", "Western Sahara"), YEMEN("YE", "Yemen"), ZAMBIA("ZM", "Zambia"),
		ZIMBABWE("ZW", "Zimbabwe");

		/**
		 * Gets a country by it's iso tag.
		 *
		 * @param isoTag The iso tag of the county.
		 * @return The country with the given iso tag or <code>null</code> if unknown.
		 */
		public static Country byIsoTag(String isoTag) {
			for (Country country : Country.values()) {
				if (country.getCountryIsoTag().equals(isoTag)) {
					return country;
				}
			}
			return null;
		}

		/**
		 * Gets a country by a locale.
		 *
		 * @param locale The locale.
		 * @return The country from the giben locale or <code>null</code> if unknown
		 *         country or if the locale does not contain a country.
		 */
		public static Country byLocale(Locale locale) {
			return byIsoTag(locale.getCountry());
		}

		private String isoTag;

		private String name;

		Country(String isoTag, String name) {
			this.isoTag = isoTag;
			this.name = name;
		}

		/**
		 * Gets the iso tag of the country.
		 *
		 * @return The iso tag of the country.
		 */
		public String getCountryIsoTag() {
			return isoTag;
		}

		/**
		 * Gets the name of the country.
		 *
		 * @return The name of the country.
		 */
		public String getCountryName() {
			return name;
		}

	}

	/**
	 * Represents a custom chart.
	 */
	public static abstract class CustomChart {

		// The id of the chart
		protected final String chartId;

		/**
		 * Class constructor.
		 *
		 * @param chartId The id of the chart.
		 */
		public CustomChart(String chartId) {
			if (chartId == null || chartId.isEmpty()) {
				throw new IllegalArgumentException("ChartId cannot be null or empty!");
			}
			this.chartId = chartId;
		}

		protected abstract JsonObject getChartData();

		protected JsonObject getRequestJsonObject() {
			JsonObject chart = new JsonObject();
			chart.addProperty("chartId", chartId);
			try {
				JsonObject data = getChartData();
				if (data == null) {
					// If the data is null we don't send the chart.
					return null;
				}
				chart.add("data", data);
			} catch (Exception e) {
				if (logFailedRequests) {
					hook.debug("Failed to get data for custom chart with id " + chartId);
					hook.debug(e);
				}
				return null;
			}
			return chart;
		}

	}

	/**
	 * Represents a custom multi line chart.
	 */
	public static abstract class MultiLineChart extends CustomChart {

		/**
		 * Class constructor.
		 *
		 * @param chartId The id of the chart.
		 */
		public MultiLineChart(String chartId) {
			super(chartId);
		}

		@Override
		protected JsonObject getChartData() {
			JsonObject data = new JsonObject();
			JsonObject values = new JsonObject();
			HashMap<String, Integer> map = getValues(new HashMap<String, Integer>());
			if (map == null || map.isEmpty()) {
				// Null = skip the chart
				return null;
			}
			boolean allSkipped = true;
			for (Map.Entry<String, Integer> entry : map.entrySet()) {
				if (entry.getValue() == 0) {
					continue; // Skip this invalid
				}
				allSkipped = false;
				values.addProperty(entry.getKey(), entry.getValue());
			}
			if (allSkipped) {
				// Null = skip the chart
				return null;
			}
			data.add("values", values);
			return data;
		}

		/**
		 * Gets the values of the chart.
		 *
		 * @param valueMap Just an empty map. The only reason it exists is to make your
		 *                 life easier. You don't have to create a map yourself!
		 * @return The values of the chart.
		 */
		public abstract HashMap<String, Integer> getValues(HashMap<String, Integer> valueMap);

	}

	/**
	 * Represents a custom simple map chart.
	 */
	public static abstract class SimpleMapChart extends CustomChart {

		/**
		 * Class constructor.
		 *
		 * @param chartId The id of the chart.
		 */
		public SimpleMapChart(String chartId) {
			super(chartId);
		}

		@Override
		protected JsonObject getChartData() {
			JsonObject data = new JsonObject();
			Country value = getValue();

			if (value == null) {
				// Null = skip the chart
				return null;
			}
			data.addProperty("value", value.getCountryIsoTag());
			return data;
		}

		/**
		 * Gets the value of the chart.
		 *
		 * @return The value of the chart.
		 */
		public abstract Country getValue();

	}

	/**
	 * Represents a custom simple pie.
	 */
	public static abstract class SimplePie extends CustomChart {

		/**
		 * Class constructor.
		 *
		 * @param chartId The id of the chart.
		 */
		public SimplePie(String chartId) {
			super(chartId);
		}

		@Override
		protected JsonObject getChartData() {
			JsonObject data = new JsonObject();
			String value = getValue();
			if (value == null || value.isEmpty()) {
				// Null = skip the chart
				return null;
			}
			data.addProperty("value", value);
			return data;
		}

		/**
		 * Gets the value of the pie.
		 *
		 * @return The value of the pie.
		 */
		public abstract String getValue();
	}

	/**
	 * Represents a custom single line chart.
	 */
	public static abstract class SingleLineChart extends CustomChart {

		/**
		 * Class constructor.
		 *
		 * @param chartId The id of the chart.
		 */
		public SingleLineChart(String chartId) {
			super(chartId);
		}

		@Override
		protected JsonObject getChartData() {
			JsonObject data = new JsonObject();
			int value = getValue();
			if (value == 0) {
				// Null = skip the chart
				return null;
			}
			data.addProperty("value", value);
			return data;
		}

		/**
		 * Gets the value of the chart.
		 *
		 * @return The value of the chart.
		 */
		public abstract int getValue();

	}

	// The version of this bStats class
	public static final int B_STATS_VERSION = 1;

	static AdvancedCorePlugin hook = AdvancedCorePlugin.getInstance();

	// Should failed requests be logged?
	private static boolean logFailedRequests;

	// The uuid of the server
	private static String serverUUID;

	// The url to which the data is sent
	private static final String URL = "https://bStats.org/submitData";

	/**
	 * Gzips the given String.
	 *
	 * @param str The string to gzip.
	 * @return The gzipped String.
	 * @throws IOException If the compression failed.
	 */
	private static byte[] compress(final String str) throws IOException {
		if (str == null) {
			return null;
		}
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		GZIPOutputStream gzip = new GZIPOutputStream(outputStream);
		gzip.write(str.getBytes("UTF-8"));
		gzip.close();
		return outputStream.toByteArray();
	}

	/**
	 * Sends the data to the bStats server.
	 *
	 * @param data The data to send.
	 * @throws Exception If the request failed.
	 */
	private static void sendData(JsonObject data) throws Exception {
		if (data == null) {
			throw new IllegalArgumentException("Data cannot be null!");
		}
		if (Bukkit.isPrimaryThread()) {
			throw new IllegalAccessException("This method must not be called from the main thread!");
		}
		HttpsURLConnection connection = (HttpsURLConnection) new URL(URL).openConnection();

		// Compress the data to save bandwidth
		byte[] compressedData = compress(data.toString());

		// Add headers
		connection.setRequestMethod("POST");
		connection.addRequestProperty("Accept", "application/json");
		connection.addRequestProperty("Connection", "close");
		connection.addRequestProperty("Content-Encoding", "gzip"); // We gzip
																	// our
																	// request
		connection.addRequestProperty("Content-Length", String.valueOf(compressedData.length));
		connection.setRequestProperty("Content-Type", "application/json"); // We
																			// send
																			// our
																			// data
																			// in
																			// JSON
																			// format
		connection.setRequestProperty("User-Agent", "MC-Server/" + B_STATS_VERSION);

		// Send data
		connection.setDoOutput(true);
		DataOutputStream outputStream = new DataOutputStream(connection.getOutputStream());
		outputStream.write(compressedData);
		outputStream.flush();
		outputStream.close();

		connection.getInputStream().close(); // We don't care about the response
												// - Just send our data :)
	}

	// A list with all custom charts
	private final List<CustomChart> charts = new ArrayList<>();

	// The plugin
	private final Plugin plugin;

	/**
	 * Class constructor.
	 *
	 * @param plugin The plugin which stats should be submitted.
	 */
	public BStatsMetrics(Plugin plugin) {
		if (plugin == null) {
			throw new IllegalArgumentException("Plugin cannot be null!");
		}
		this.plugin = plugin;

		// Get the config file
		File bStatsFolder = new File(plugin.getDataFolder().getParentFile(), "bStats");
		File configFile = new File(bStatsFolder, "config.yml");
		YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

		// Check if the config file exists
		if (!config.isSet("serverUuid")) {

			// Add default values
			config.addDefault("enabled", true);
			// Every server gets it's unique random id.
			config.addDefault("serverUuid", UUID.randomUUID().toString());
			// Should failed request be logged?
			config.addDefault("logFailedRequests", false);

			// Inform the server owners about bStats
			config.options().header(
					"bStats collects some data for plugin authors like how many servers are using their plugins.\n"
							+ "To honor their work, you should not disable it.\n"
							+ "This has nearly no effect on the server performance!\n"
							+ "Check out https://bStats.org/ to learn more :)")
					.copyDefaults(true);
			try {
				config.save(configFile);
			} catch (IOException ignored) {
			}
		}

		// Load the data
		serverUUID = config.getString("serverUuid");
		logFailedRequests = config.getBoolean("logFailedRequests", false);
		if (config.getBoolean("enabled", true)) {
			boolean found = false;
			// Search for all other bStats Metrics classes to see if we are the
			// first one
			for (Class<?> service : Bukkit.getServicesManager().getKnownServices()) {
				try {
					service.getField("B_STATS_VERSION"); // Our identifier :)
					found = true; // We aren't the first
					break;
				} catch (NoSuchFieldException ignored) {
				}
			}
			// Register our service
			Bukkit.getServicesManager().register(BStatsMetrics.class, this, plugin, ServicePriority.Normal);
			if (!found) {
				// We are the first!
				startSubmitting();
			}
		}
	}

	/**
	 * Adds a custom chart.
	 *
	 * @param chart The chart to add.
	 */
	public void addCustomChart(CustomChart chart) {
		if (chart == null) {
			throw new IllegalArgumentException("Chart cannot be null!");
		}
		charts.add(chart);
	}

	/**
	 * Gets the plugin specific data. This method is called using Reflection.
	 *
	 * @return The plugin specific data.
	 */
	public JsonObject getPluginData() {
		JsonObject data = new JsonObject();

		String pluginName = plugin.getDescription().getName();
		String pluginVersion = plugin.getDescription().getVersion();

		data.addProperty("pluginName", pluginName); // Append the name of the plugin
		data.addProperty("pluginVersion", pluginVersion); // Append the version of the
		// plugin
		JsonArray customCharts = new JsonArray();
		for (CustomChart customChart : charts) {
			// Add the data of the custom charts
			JsonObject chart = customChart.getRequestJsonObject();
			if (chart == null) { // If the chart is null, we skip it
				continue;
			}
			customCharts.add(chart);
		}
		data.add("customCharts", customCharts);

		return data;
	}

	/**
	 * Gets the server specific data.
	 *
	 * @return The server specific data.
	 */
	private JsonObject getServerData() {
		// Minecraft specific data
		int playerAmount = Bukkit.getOnlinePlayers().size();
		int onlineMode = Bukkit.getOnlineMode() ? 1 : 0;
		String bukkitVersion = org.bukkit.Bukkit.getVersion();
		bukkitVersion = bukkitVersion.substring(bukkitVersion.indexOf("MC: ") + 4, bukkitVersion.length() - 1);

		// OS/Java specific data
		String javaVersion = System.getProperty("java.version");
		String osName = System.getProperty("os.name");
		String osArch = System.getProperty("os.arch");
		String osVersion = System.getProperty("os.version");
		int coreCount = Runtime.getRuntime().availableProcessors();

		JsonObject data = new JsonObject();

		data.addProperty("serverUUID", serverUUID);

		data.addProperty("playerAmount", playerAmount);
		data.addProperty("onlineMode", onlineMode);
		data.addProperty("bukkitVersion", bukkitVersion);

		data.addProperty("javaVersion", javaVersion);
		data.addProperty("osName", osName);
		data.addProperty("osArch", osArch);
		data.addProperty("osVersion", osVersion);
		data.addProperty("coreCount", coreCount);

		return data;
	}

	/**
	 * Starts the Scheduler which submits our data every 30 minutes.
	 */
	private void startSubmitting() {
		final Timer timer = new Timer(true); // We use a timer cause the Bukkit
												// scheduler is affected by
												// server lags
		timer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				if (!plugin.isEnabled()) { // Plugin was disabled
					timer.cancel();
					return;
				}
				// Nevertheless we want our code to run in the Bukkit main
				// thread, so we have to use the Bukkit scheduler
				// Don't be afraid! The connection to the bStats server is still
				// async, only the stats collection is sync ;)
				Bukkit.getScheduler().runTask(plugin, new Runnable() {
					@Override
					public void run() {
						submitData();
					}
				});
			}
		}, 1000 * 60 * 5, 1000 * 60 * 30);
		// Submit the data every 30 minutes, first time after 5 minutes to give
		// other plugins enough time to start
		// WARNING: Changing the frequency has no effect but your plugin WILL be
		// blocked/deleted!
		// WARNING: Just don't do it!
	}

	/**
	 * Collects the data and sends it afterwards.
	 */
	private void submitData() {
		// Create a new thread for the connection to the bStats server
		new Thread(new Runnable() {
			@Override
			public void run() {
				final JsonObject data = getServerData();

				JsonArray pluginData = new JsonArray();
				// Search for all other bStats Metrics classes to get their
				// plugin data
				for (Class<?> service : Bukkit.getServicesManager().getKnownServices()) {
					try {
						service.getField("B_STATS_VERSION"); // Our identifier
																// :)
					} catch (NoSuchFieldException ignored) {
						continue; // Continue "searching"
					}
					// Found one!
					try {
						pluginData.add(service.getMethod("getPluginData")
								.invoke(Bukkit.getServicesManager().load(service)).toString());
					} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
					}
				}

				data.add("plugins", pluginData);
				try {
					// Send the data
					sendData(data);
				} catch (Exception e) {
					// Something went wrong! :(
					if (logFailedRequests) {
						hook.debug("Could not submit plugin stats of " + plugin.getName());
						hook.debug(e);
					}
				}
			}
		}).start();

	}

}
