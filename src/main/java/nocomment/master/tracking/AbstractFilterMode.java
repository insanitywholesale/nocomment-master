package nocomment.master.tracking;

import java.util.List;
import nocomment.master.util.ChunkPos;

public abstract class AbstractFilterMode {

	public abstract List<ChunkPos> updateStep(List<ChunkPos> hits, List<ChunkPos> misses);

	public abstract boolean includesBroadly(ChunkPos pos);

	public abstract void decommission();

	public FilterModeEnum getEnum() {
		return FilterModeEnum.CLASS_MAP.get(getClass());
	}

}
