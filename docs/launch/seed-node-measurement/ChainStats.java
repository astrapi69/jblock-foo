import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import io.github.astrapi69.lethenon.BlockBody;
import io.github.astrapi69.lethenon.CanonicalEncoding;

/**
 * Reads a chain file with the published lethenon library and prints one line per block: height,
 * timestamp in ms, seconds since the block before, difficulty, transfers, encoded size in bytes.
 * Usage: java -cp "lib/*" ChainStats.java chain.lethenon
 */
public class ChainStats
{
	public static void main(String[] args) throws Exception
	{
		List<BlockBody> chain = CanonicalEncoding.readChain(Files.readAllBytes(Path.of(args[0])));
		long previous = -1;
		System.out.println("height,timestamp_ms,interval_s,difficulty,transfers,bytes");
		for (BlockBody block : chain)
		{
			double interval = previous < 0 ? 0 : (block.timestamp() - previous) / 1000.0;
			System.out.println(block.height() + "," + block.timestamp() + "," + interval + ","
				+ block.difficulty() + "," + block.transactions().size() + ","
				+ CanonicalEncoding.blockSize(block));
			previous = block.timestamp();
		}
	}
}
