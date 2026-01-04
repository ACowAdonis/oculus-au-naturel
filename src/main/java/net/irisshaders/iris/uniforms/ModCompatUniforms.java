package net.irisshaders.iris.uniforms;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.uniform.UniformHolder;
import net.irisshaders.iris.gl.uniform.UniformUpdateFrequency;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.fml.loading.LoadingModList;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Provides shader uniforms for mod compatibility, specifically for:
 * - Serene Seasons: Season data (currentSeason, subSeason, seasonProgress, yearProgress, etc.)
 * - Cold Sweat: Temperature data (playerBodyTemp, worldAmbientTemp)
 *
 * All season uniforms are designed to work with configurable season lengths.
 * When mods are not present, sensible defaults are provided.
 */
public class ModCompatUniforms {
	private static final Minecraft client = Minecraft.getInstance();

	// Mod presence flags - checked once at startup
	private static final boolean SERENE_SEASONS_LOADED;
	private static final boolean COLD_SWEAT_LOADED;

	// Serene Seasons method handles
	private static MethodHandle getSeasonState;
	private static MethodHandle getSeason;
	private static MethodHandle getSubSeason;
	private static MethodHandle getSeasonCycleTicks;
	private static MethodHandle getSeasonDuration;
	private static MethodHandle getCycleDuration;
	private static MethodHandle getDayDuration;
	private static MethodHandle getSeasonOrdinal;
	private static MethodHandle getSubSeasonOrdinal;

	// Cold Sweat method handles
	private static MethodHandle getTemperature;
	private static MethodHandle getWorldTemperatureAt;
	private static Object traitCore;
	private static Object traitWorld;

	// Cached values for performance (updated at controlled intervals)
	// Serene Seasons
	private static int cachedSeason = 1;           // Default: summer (0-3)
	private static int cachedSubSeason = 4;        // Default: mid-summer (0-11)
	private static float cachedSeasonProgress = 0.5f;  // Progress through current season (0-1)
	private static float cachedYearProgress = 0.375f;  // Progress through year (0-1), default ~mid-summer
	private static int cachedSeasonDay = 12;       // Day within current season (0 to daysPerSeason-1)
	private static int cachedDaysPerSeason = 24;   // Configurable days per season (default 24)

	// Cold Sweat
	private static float cachedPlayerBodyTemp = 0.0f;
	private static float cachedWorldAmbientTemp = 0.0f;

	// Frame counter for controlling update frequency
	private static int frameCounter = 0;
	private static final int SEASON_UPDATE_INTERVAL = 1200; // ~20 seconds at 60fps
	private static final int TEMP_UPDATE_INTERVAL = 90;     // ~1.5 seconds at 60fps

	static {
		SERENE_SEASONS_LOADED = LoadingModList.get().getModFileById("sereneseasons") != null;
		COLD_SWEAT_LOADED = LoadingModList.get().getModFileById("cold_sweat") != null;

		if (SERENE_SEASONS_LOADED) {
			initSereneSeasonsCompat();
		} else {
			Iris.logger.info("Serene Seasons not found, using default season values");
		}

		if (COLD_SWEAT_LOADED) {
			initColdSweatCompat();
		} else {
			Iris.logger.info("Cold Sweat not found, using default temperature values");
		}
	}

	private static void initSereneSeasonsCompat() {
		try {
			MethodHandles.Lookup lookup = MethodHandles.lookup();

			// Load classes from sereneseasons.api.season package
			Class<?> seasonHelperClass = Class.forName("sereneseasons.api.season.SeasonHelper");
			Class<?> seasonStateClass = Class.forName("sereneseasons.api.season.ISeasonState");
			Class<?> seasonClass = Class.forName("sereneseasons.api.season.Season");
			Class<?> subSeasonClass = Class.forName("sereneseasons.api.season.Season$SubSeason");

			// SeasonHelper.getSeasonState(Level) returns ISeasonState
			getSeasonState = lookup.findStatic(seasonHelperClass, "getSeasonState",
				MethodType.methodType(seasonStateClass, Level.class));

			// ISeasonState.getSeason() returns Season enum
			getSeason = lookup.findVirtual(seasonStateClass, "getSeason",
				MethodType.methodType(seasonClass));

			// ISeasonState.getSubSeason() returns Season.SubSeason enum
			getSubSeason = lookup.findVirtual(seasonStateClass, "getSubSeason",
				MethodType.methodType(subSeasonClass));

			// ISeasonState.getSeasonCycleTicks() returns int (current tick position in year)
			getSeasonCycleTicks = lookup.findVirtual(seasonStateClass, "getSeasonCycleTicks",
				MethodType.methodType(int.class));

			// ISeasonState.getSeasonDuration() returns int (ticks per season)
			getSeasonDuration = lookup.findVirtual(seasonStateClass, "getSeasonDuration",
				MethodType.methodType(int.class));

			// ISeasonState.getCycleDuration() returns int (ticks per year)
			getCycleDuration = lookup.findVirtual(seasonStateClass, "getCycleDuration",
				MethodType.methodType(int.class));

			// ISeasonState.getDayDuration() returns int (ticks per day, usually 24000)
			getDayDuration = lookup.findVirtual(seasonStateClass, "getDayDuration",
				MethodType.methodType(int.class));

			// Season.ordinal() and SubSeason.ordinal()
			getSeasonOrdinal = lookup.findVirtual(seasonClass, "ordinal",
				MethodType.methodType(int.class));
			getSubSeasonOrdinal = lookup.findVirtual(subSeasonClass, "ordinal",
				MethodType.methodType(int.class));

			Iris.logger.info("Serene Seasons compatibility initialized successfully");
		} catch (Throwable e) {
			Iris.logger.warn("Failed to initialize Serene Seasons compatibility: " + e.getMessage());
			getSeasonState = null;
		}
	}

	private static void initColdSweatCompat() {
		try {
			MethodHandles.Lookup lookup = MethodHandles.lookup();

			Class<?> temperatureClass = Class.forName("com.momosoftworks.coldsweat.api.util.Temperature");
			Class<?> traitClass = Class.forName("com.momosoftworks.coldsweat.api.util.Temperature$Trait");

			getTemperature = lookup.findStatic(temperatureClass, "get",
				MethodType.methodType(double.class, net.minecraft.world.entity.LivingEntity.class, traitClass));

			Object[] traits = traitClass.getEnumConstants();
			for (Object trait : traits) {
				String name = trait.toString();
				if ("CORE".equals(name)) {
					traitCore = trait;
				} else if ("WORLD".equals(name)) {
					traitWorld = trait;
				}
			}

			if (traitCore == null || traitWorld == null) {
				throw new RuntimeException("Could not find CORE or WORLD traits in Temperature.Trait enum");
			}

			try {
				Class<?> worldHelperClass = Class.forName("com.momosoftworks.coldsweat.util.world.WorldHelper");
				getWorldTemperatureAt = lookup.findStatic(worldHelperClass, "getTemperatureAt",
					MethodType.methodType(double.class, Level.class, BlockPos.class));
				Iris.logger.info("Cold Sweat WorldHelper available for precise world temperature");
			} catch (Throwable e) {
				getWorldTemperatureAt = null;
				Iris.logger.debug("Cold Sweat WorldHelper not available, using Temperature.get(WORLD) instead");
			}

			Iris.logger.info("Cold Sweat compatibility initialized successfully");
		} catch (Throwable e) {
			Iris.logger.warn("Failed to initialize Cold Sweat compatibility: " + e.getMessage());
			getTemperature = null;
			traitCore = null;
			traitWorld = null;
		}
	}

	/**
	 * Registers all mod compatibility uniforms.
	 */
	public static void addModCompatUniforms(UniformHolder uniforms) {
		// Serene Seasons uniforms
		uniforms.uniform1i(UniformUpdateFrequency.PER_TICK, "currentSeason", ModCompatUniforms::getCurrentSeason);
		uniforms.uniform1i(UniformUpdateFrequency.PER_TICK, "currentSubSeason", ModCompatUniforms::getCurrentSubSeason);
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "seasonProgress", ModCompatUniforms::getSeasonProgressValue);
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "yearProgress", ModCompatUniforms::getYearProgressValue);
		uniforms.uniform1i(UniformUpdateFrequency.PER_TICK, "seasonDay", ModCompatUniforms::getSeasonDay);
		uniforms.uniform1i(UniformUpdateFrequency.PER_TICK, "daysPerSeason", ModCompatUniforms::getDaysPerSeason);

		// Cold Sweat uniforms
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "playerBodyTemp", ModCompatUniforms::getPlayerBodyTemp);
		uniforms.uniform1f(UniformUpdateFrequency.PER_TICK, "worldAmbientTemp", ModCompatUniforms::getWorldAmbientTemp);
	}

	/**
	 * Called each tick to potentially update cached values.
	 */
	private static void updateCachedValues() {
		frameCounter++;

		if (SERENE_SEASONS_LOADED && getSeasonState != null && frameCounter % SEASON_UPDATE_INTERVAL == 0) {
			updateSeasonData();
		}

		if (COLD_SWEAT_LOADED && getTemperature != null && frameCounter % TEMP_UPDATE_INTERVAL == 0) {
			updateTemperatureData();
		}
	}

	private static void updateSeasonData() {
		ClientLevel level = client.level;
		if (level == null) return;

		try {
			Object seasonState = getSeasonState.invoke(level);
			if (seasonState == null) return;

			// Get current season (SPRING=0, SUMMER=1, AUTUMN=2, WINTER=3)
			Object season = getSeason.invoke(seasonState);
			if (season != null) {
				cachedSeason = (int) getSeasonOrdinal.invoke(season);
			}

			// Get current sub-season (0-11: EARLY_SPRING through LATE_WINTER)
			Object subSeason = getSubSeason.invoke(seasonState);
			if (subSeason != null) {
				cachedSubSeason = (int) getSubSeasonOrdinal.invoke(subSeason);
			}

			// Get timing values
			int cycleTicks = (int) getSeasonCycleTicks.invoke(seasonState);
			int seasonDuration = (int) getSeasonDuration.invoke(seasonState);
			int cycleDuration = (int) getCycleDuration.invoke(seasonState);
			int dayDuration = (int) getDayDuration.invoke(seasonState);

			// Calculate season progress (0.0 to 1.0 through current season)
			if (seasonDuration > 0) {
				cachedSeasonProgress = (float) (cycleTicks % seasonDuration) / (float) seasonDuration;
			}

			// Calculate year progress (0.0 to 1.0 through entire year)
			if (cycleDuration > 0) {
				cachedYearProgress = (float) cycleTicks / (float) cycleDuration;
			}

			// Calculate days per season (for shader reference)
			if (dayDuration > 0) {
				cachedDaysPerSeason = seasonDuration / dayDuration;
			}

			// Calculate day within current season (0 to daysPerSeason-1)
			if (dayDuration > 0 && seasonDuration > 0) {
				int ticksIntoSeason = cycleTicks % seasonDuration;
				cachedSeasonDay = ticksIntoSeason / dayDuration;
			}

		} catch (Throwable e) {
			Iris.logger.debug("Error updating season data: " + e.getMessage());
		}
	}

	private static void updateTemperatureData() {
		LocalPlayer player = client.player;
		ClientLevel level = client.level;
		if (player == null || level == null) return;

		try {
			if (traitCore != null) {
				cachedPlayerBodyTemp = ((Number) getTemperature.invoke(player, traitCore)).floatValue();
			}

			if (getWorldTemperatureAt != null) {
				cachedWorldAmbientTemp = ((Number) getWorldTemperatureAt.invoke(level, player.blockPosition())).floatValue();
			} else if (traitWorld != null) {
				cachedWorldAmbientTemp = ((Number) getTemperature.invoke(player, traitWorld)).floatValue();
			}

		} catch (Throwable e) {
			Iris.logger.debug("Error updating temperature data: " + e.getMessage());
		}
	}

	// Uniform getter methods - Serene Seasons

	private static int getCurrentSeason() {
		updateCachedValues();
		return cachedSeason;
	}

	private static int getCurrentSubSeason() {
		updateCachedValues();
		return cachedSubSeason;
	}

	private static float getSeasonProgressValue() {
		updateCachedValues();
		return cachedSeasonProgress;
	}

	private static float getYearProgressValue() {
		updateCachedValues();
		return cachedYearProgress;
	}

	private static int getSeasonDay() {
		updateCachedValues();
		return cachedSeasonDay;
	}

	private static int getDaysPerSeason() {
		updateCachedValues();
		return cachedDaysPerSeason;
	}

	// Uniform getter methods - Cold Sweat

	private static float getPlayerBodyTemp() {
		updateCachedValues();
		return cachedPlayerBodyTemp;
	}

	private static float getWorldAmbientTemp() {
		updateCachedValues();
		return cachedWorldAmbientTemp;
	}

	// Utility methods for checking mod availability

	public static boolean isSereneSeasonsLoaded() {
		return SERENE_SEASONS_LOADED && getSeasonState != null;
	}

	public static boolean isColdSweatLoaded() {
		return COLD_SWEAT_LOADED && getTemperature != null;
	}
}
