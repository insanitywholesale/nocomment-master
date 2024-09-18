package nocomment.master.tracking;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import nocomment.master.util.ChunkPos;

public enum FilterModeEnum {

	MONTE_CARLO_PARTICLE_FILTER(MonteCarloParticleFilterMode::new,
			MonteCarloParticleFilterMode.class), STATIONARY_FILTER(StationaryFilterMode::new,
					StationaryFilterMode.class);

	public final BiFunction<ChunkPos, Track, AbstractFilterMode> constructor;

	public final Class<? extends AbstractFilterMode> klass;

	FilterModeEnum(BiFunction<ChunkPos, Track, AbstractFilterMode> constructor,
			Class<? extends AbstractFilterMode> klass) {
		this.constructor = constructor;
		this.klass = klass;
	}

	public int priority() {
		return ordinal();
	}

	public static Map<Class<? extends AbstractFilterMode>, FilterModeEnum> CLASS_MAP;

	static {
		Map<Class<? extends AbstractFilterMode>, FilterModeEnum> tmp = new HashMap<>();
		for (FilterModeEnum mode : values()) {
			tmp.put(mode.klass, mode);
		}
		CLASS_MAP = Collections.unmodifiableMap(tmp);
	}

}
