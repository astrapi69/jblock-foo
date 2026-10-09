import io.github.astrapi69.lethenon.*;
import java.nio.file.*;
import java.util.List;

/** Writes a main-chain genesis block as the start day would, to the file named first */
public class GenesisFile {
    public static void main(String[] args) throws Exception {
        BlockBody genesis = Genesis.candidate(Chain.IDENTIFIER, args[1], System.currentTimeMillis());
        Files.write(Path.of(args[0]), CanonicalEncoding.encodeChain(List.of(genesis)));
        System.out.println("wrote block 0 of " + genesis.chainIdentifier() + " paying " + genesis.beneficiary());
    }
}
