package nocomment.master.task;

import java.util.function.Consumer;
import nocomment.master.db.Hit;
import nocomment.master.util.ChunkPos;

public final class SingleChunkTask extends TaskHelper {

	public SingleChunkTask(int priority, ChunkPos pos, Consumer<Hit> onHit, Runnable onMiss) {
		super(priority, pos, 0, 0, 1, onHit, numHits -> {
			if (numHits == 0) {
				onMiss.run();
			}
		});
	}

}
