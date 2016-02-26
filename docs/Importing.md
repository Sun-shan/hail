# Importing

Hail does not operate directly on VCF files.  Hail uses a fast and storage-efficient internal representation called a VDS (variant dataset).  In order to use Hail for data analysis, data must first be imported to the VDS format.  This is done with the `importvcf` command.  Hail is designed to be maximally compatible with files in the [VCF v4.2 spec](https://samtools.github.io/hts-specs/VCFv4.2.pdf).

Command line options:
 - `-d | --no-compress` -- Do not compress VDS.  Not recommended.
 - `-f | --force` -- Force load `.gz` file.  Not recommended (see below).
 - `--header-file <file>` -- File to load VCF header from.  By default, `importvcf` reads the header from the first file listed.
 - `-n <N> | --npartitions <N>` -- Number of partitions, advanced user option.
 - `--store-gq` -- Store GQ rather than computing it from PL.  Intended for use with the Michigan GotCloud calling pipeline which stores PLs but sets the GQ to the quality of the posterior probabilities.  Disables the GQ representation checks (GQ present iff PL present, GQ the difference of two smallest PL entries).  This option is experimental and will be removed when Hail supports posterior probabilities (PP).

`importvcf` takes a list of VCF files to load.  All files must have the same header and the same set of samples in the same order (e.g., a dataset split by chromosome).  Files can be specified as Hadoop glob patterns:
 - `?` -- Matches any single character.
 - `*` -- Matches zero or more characters.
 - `[abc]` -- Matches a single character from character set {a,b,c}.
 - `[a-b]` -- Matches a single character from the character range {a...b}. Note that character a must be lexicographically less than or equal to character b.
 - `[^a]` -- Matches a single character that is not from character set or range {a}. Note that the ^ character must occur immediately to the right of the opening bracket.
 - `\c` -- Removes (escapes) any special meaning of character c.
 - `{ab,cd}` -- Matches a string from the string set {ab, cd}.
 - `{ab,c{de,fh}}` -- Matches a string from the string set {ab, cde, cfh}.

## Importing VCF files with the `importvcf` command

 - Ensure that the VCF file is correctly prepared for import:
   - VCFs should be either uncompressed (".vcf") or block-compressed (".vcf.bgz").  If you have a large compressed VCF that ends in ".vcf.gz", it is likely that the file is actually block-compressed, and you should rename the file to ".vcf.bgz" accordingly.  If you actually have a standard gzipped file, it is possible to import it to hail using the `-f` option.  However, this is not recommended -- all parsing will have to take place on one node, because gzip decompression is not parallelizable.  In this case, import could take significantly magnitude longer.
   - VCFs should reside to the hadoop file system
 - Run a hail command with `importvcf`.  The below command will read a .vcf.bgz file and write to a .vds file (Hail's preferred format).  It is possible to import and operate directly on a VCF file without first doing an import/write step, but this will greatly increase compute time if multiple commands are run (it is significantly faster to read a vds than import a vcf).
``` 
$ hail importvcf /path/to/file.vcf.bgz write -o /path/to/output.vds
```
 - Hail makes certain assumptions about the genotype fields, see [Representation](https://github.com/broadinstitute/hail/blob/master/docs/Representation.md).  On import, Hail filters (sets to no-call) any genotype that violates these assumptions.  Hail interpets the format fields: GT, AD, OD, DP, GQ, PL; all others are silently dropped.
