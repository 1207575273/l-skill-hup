package com.landray.skillhub.domain.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.YAMLException;

import com.landray.skillhub.domain.exception.SkillPackageInvalidException;
import com.landray.skillhub.domain.model.AllowedToolsParser;
import com.landray.skillhub.domain.model.ParsedSkillPackage;
import com.landray.skillhub.domain.model.RepackagedArtifact;
import com.landray.skillhub.domain.model.Sha256;
import com.landray.skillhub.domain.model.SkillName;
import com.landray.skillhub.domain.model.UploadLimits;

/**
 * skill 安装包的服务端校验与重新打包引擎(业务建模文档 5.1/5.2/5.3)。
 *
 * <p><b>严格按 5.3 的 12 步执行顺序落地,方法按步骤编号命名 / 排列,不能调换。</b>其中第 8 步(定最终 name)
 * 需要查库判重名,不是纯函数,留给 application 层决定;因此本类拆成两段 public API:
 *
 * <ul>
 *   <li>{@link #parseAndValidate(byte[])} —— 第 1~7 步,产出 {@link ParsedSkillPackage}(含
 *       {@code content_digest},此时还没有决定最终 name,frontmatter 也还没有被改写)。
 *   <li>{@link #repackage(ParsedSkillPackage, SkillName)} —— 第 9~11 步,拿到 application 层已经决定好的
 *       最终 name 后,改写 frontmatter -&gt; 按最终 name 重新打包 -&gt; 算最终 zip 的 sha256。
 * </ul>
 *
 * <p>{@code precheck} 与正式上传执行完全相同的这两段逻辑,唯一的差别是调用方(见
 * {@code application.upload.SkillUploadApplicationService}):precheck 只调用
 * {@link #parseAndValidate(byte[])} 且不落库,正式发布额外调用 {@link #repackage} 并把结果写进
 * {@code ArtifactStorage} 与数据库。
 *
 * <p><b>为什么读 zip 用 commons-compress 而不是 stdlib {@code java.util.zip}</b>:第 2 步"拒绝符号链接"需要
 * 读取 zip 条目的 Unix 外部文件属性,而 {@code java.util.zip.ZipEntry} 不暴露这个信息(JDK 标准库的已知限制,
 * 没有公开 API 可以绕开,手写解析 ZIP 中央目录属于"正是最容易写出安全 bug 的那类底层二进制解析")。写(第 10
 * 步重新打包)仍然用 stdlib {@code java.util.zip.ZipOutputStream}——业务建模文档 3.7 已经论证过
 * {@code sha256} 不追求可复现,没必要为了写 Unix 权限位再换一个库,读写两侧用不同的库职责边界清楚。
 *
 * <p>domain 纯净性:本类只依赖 JDK 标准库、commons-compress、snakeyaml 三者,均非 Spring/MyBatis/Jackson,
 * 满足 {@code LayeredArchitectureTest#domainMustBePlainJava}。
 */
public final class SkillPackageValidator {

    private static final String CODE_TOO_LARGE = "SKILL_PACKAGE_TOO_LARGE";
    private static final String CODE_TOO_MANY_ENTRIES = "SKILL_PACKAGE_TOO_MANY_ENTRIES";
    private static final String CODE_FILE_TOO_LARGE = "SKILL_PACKAGE_FILE_TOO_LARGE";
    private static final String CODE_PATH_TOO_DEEP = "SKILL_PACKAGE_PATH_TOO_DEEP";
    private static final String CODE_PATH_TOO_LONG = "SKILL_PACKAGE_PATH_TOO_LONG";
    private static final String CODE_ZIP_BOMB = "SKILL_PACKAGE_ZIP_BOMB";
    private static final String CODE_PATH_UNSAFE = "SKILL_PACKAGE_PATH_UNSAFE";
    private static final String CODE_SYMLINK = "SKILL_PACKAGE_SYMLINK_NOT_ALLOWED";
    private static final String CODE_STRUCTURE_INVALID = "SKILL_PACKAGE_STRUCTURE_INVALID";
    private static final String CODE_FRONTMATTER_INVALID = "SKILL_PACKAGE_FRONTMATTER_INVALID";
    private static final String CODE_NOT_A_ZIP = "SKILL_PACKAGE_NOT_A_ZIP";

    /** 用户提交的原始 name 上限(业务建模文档 5.5):skill.name 列宽 64,留 12 位给 "-" + 短哈希后缀。 */
    private static final int MAX_RAW_NAME_LENGTH = 50;

    /** 与 skill.description 列宽(VARCHAR(512))一致,业务建模文档 4.8。 */
    /**
     * Agent Skills 官方规范:{@code description} 非空、最多 1024 字符。
     *
     * <p>曾经写死 512,依据是数据库列宽 {@code VARCHAR(512)} —— 那是把存储实现当成了业务规则。
     * 实测真实 skill 的 description 轻易超过 512(报过 616 的),按 512 卡等于拒收合规包。
     * 列宽已由 V0003 放宽到 1024 与规范对齐。
     */
    private static final int MAX_DESCRIPTION_LENGTH = 1024;

    /** frontmatter YAML 块的字符数上限,防 YAML 炸弹(billion-laughs 之类的别名展开攻击)——防御性设置,
     * 正常 frontmatter 远小于这个数。SnakeYAML 2.x 的 {@link LoaderOptions} 默认值本身已经比较安全
     * (maxAliasesForCollections/nestingDepthLimit 都有出厂默认上限),这里再显式收紧一道 codePointLimit,
     * 属于纵深防御,不是补一个原本没有的洞。 */
    private static final int FRONTMATTER_CODE_POINT_LIMIT = 65_536;

    private static final Set<String> JUNK_SEGMENTS = Set.of("__MACOSX", ".git", "node_modules");
    private static final Set<String> JUNK_FILE_NAMES = Set.of(".DS_Store", "Thumbs.db");

    private static final String SKILL_MD = "SKILL.md";
    private static final String README_MD = "README.md";

    private static final Pattern NAME_LINE_PATTERN = Pattern.compile("^name:\\s*.*$");
    private static final String FRONTMATTER_DELIMITER = "---";

    private static final int BUFFER_SIZE = 8192;

    private final UploadLimits limits;

    public SkillPackageValidator(UploadLimits limits) {
        this.limits = Objects.requireNonNull(limits, "limits 不能为空");
    }

    /** 第 1~7 步:流式解压 + 全部安全与结构校验 + 算 content_digest。改名之前,frontmatter 还是原始内容。 */
    public ParsedSkillPackage parseAndValidate(byte[] zipBytes) {
        Map<String, byte[]> rawFiles = readAndEnforceLimits(zipBytes); // 步骤 1(含内联的第 2 步路径/符号链接校验)
        Map<String, byte[]> withoutJunk = stripJunk(rawFiles); // 步骤 3
        Map<String, byte[]> canonical = stripTopLevelWrapper(withoutJunk); // 步骤 4
        validateStructure(canonical); // 步骤 5(含剥壳后的目录深度校验)
        ParsedFrontmatterFields frontmatter = parseFrontmatter(canonical.get(SKILL_MD)); // 步骤 6
        String contentDigest = computeContentDigest(canonical); // 步骤 7,在改名之前算

        String readmeContent = canonical.containsKey(README_MD)
                ? new String(canonical.get(README_MD), StandardCharsets.UTF_8)
                : null;
        long unpackedBytes = canonical.values().stream().mapToLong(bytes -> bytes.length).sum();

        return new ParsedSkillPackage(
                frontmatter.name(),
                frontmatter.description(),
                frontmatter.allowedTools(),
                contentDigest,
                canonical.size(),
                unpackedBytes,
                readmeContent,
                canonical);
    }

    /** 第 9~11 步:改写 frontmatter 的 name -&gt; 按最终 name 重新打包 -&gt; 算最终 zip 的 sha256。 */
    public RepackagedArtifact repackage(ParsedSkillPackage parsed, SkillName finalName) {
        byte[] originalSkillMd = parsed.skillMdBytes();
        if (originalSkillMd == null) {
            throw invalid(CODE_STRUCTURE_INVALID, "包内缺少 SKILL.md,无法重新打包");
        }
        String rewrittenSkillMd =
                rewriteFrontmatterName(new String(originalSkillMd, StandardCharsets.UTF_8), finalName.value());
        byte[] zipBytes = buildZip(parsed.files(), rewrittenSkillMd, finalName.value());
        String sha256 = Sha256.hex(zipBytes);
        return new RepackagedArtifact(zipBytes, sha256, rewrittenSkillMd);
    }

    // ------------------------------------------------------------------
    // 步骤 1 + 2:流式读取,实时执行 5.2 全部上限;路径安全与符号链接校验
    // ------------------------------------------------------------------

    private Map<String, byte[]> readAndEnforceLimits(byte[] zipBytes) {
        Map<String, byte[]> result = new LinkedHashMap<>();
        try (SeekableInMemoryByteChannel channel = new SeekableInMemoryByteChannel(zipBytes);
                ZipFile zipFile = ZipFile.builder().setSeekableByteChannel(channel).get()) {
            int entryCount = 0;
            long totalUnpacked = 0;
            Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                entryCount++;
                if (entryCount > limits.maxEntryCount()) {
                    throw invalid(CODE_TOO_MANY_ENTRIES, "zip 条目数超过上限 " + limits.maxEntryCount());
                }

                String rawName = entry.getName();
                validatePathSafety(rawName);
                if (rawName.length() > limits.maxPathLength()) {
                    throw invalid(CODE_PATH_TOO_LONG, "路径长度超过上限 " + limits.maxPathLength() + " 个字符: " + rawName);
                }
                if (entry.isUnixSymlink()) {
                    throw invalid(CODE_SYMLINK, "不允许符号链接条目: " + rawName);
                }
                if (entry.isDirectory()) {
                    continue;
                }

                byte[] content = readBounded(zipFile.getInputStream(entry), limits.maxFileBytes(), rawName);
                totalUnpacked += content.length;
                if (totalUnpacked > limits.maxUnpackedBytes()) {
                    throw invalid(CODE_TOO_LARGE, "解压后总大小超过上限 " + limits.maxUnpackedBytes() + " 字节");
                }
                checkCompressionRatio(entry, content, rawName);

                result.put(rawName, content);
            }
        } catch (IOException e) {
            throw invalid(CODE_NOT_A_ZIP, "无法解析为合法的 zip 包: " + e.getMessage());
        }
        return result;
    }

    private byte[] readBounded(InputStream rawIn, long cap, String entryName) throws IOException {
        try (InputStream in = rawIn) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[BUFFER_SIZE];
            long total = 0;
            int read;
            while ((read = in.read(chunk)) != -1) {
                total += read;
                if (total > cap) {
                    throw invalid(CODE_FILE_TOO_LARGE, "单个文件超过上限 " + cap + " 字节: " + entryName);
                }
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        }
    }

    /**
     * 压缩比校验:{@code getCompressedSize()} 来自 zip 中央目录,只有在这个值是已知的正数时才有意义——
     * 极少数情况下它可能是 -1(未知),此时跳过而不是把它当 1 处理(那会让任何正常大小的文件都被误判成炸弹)。
     */
    private void checkCompressionRatio(ZipArchiveEntry entry, byte[] content, String rawName) {
        long compressedSize = entry.getCompressedSize();
        if (compressedSize <= 0) {
            return;
        }
        if (content.length > compressedSize * (long) limits.maxCompressionRatio()) {
            throw invalid(
                    CODE_ZIP_BOMB,
                    "压缩比超过上限 " + limits.maxCompressionRatio() + ":1, entry=" + rawName + ", compressedSize="
                            + compressedSize + ", unpackedSize=" + content.length);
        }
    }

    private void validatePathSafety(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw invalid(CODE_PATH_UNSAFE, "zip 条目名不能为空");
        }
        if (rawName.chars().anyMatch(Character::isISOControl)) {
            throw invalid(CODE_PATH_UNSAFE, "zip 条目名包含控制字符");
        }
        String normalized = rawName.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[A-Za-z]:.*")) {
            throw invalid(CODE_PATH_UNSAFE, "zip 条目名不允许是绝对路径: " + rawName);
        }
        for (String segment : normalized.split("/", -1)) {
            if (segment.equals("..")) {
                throw invalid(CODE_PATH_UNSAFE, "zip 条目名不允许包含 ..: " + rawName);
            }
        }
    }

    // ------------------------------------------------------------------
    // 步骤 3:剥垃圾——必须排在剥顶层包裹目录(步骤 4)之前,这个先后本身是承重的
    // ------------------------------------------------------------------

    private Map<String, byte[]> stripJunk(Map<String, byte[]> raw) {
        Map<String, byte[]> result = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : raw.entrySet()) {
            if (!isJunk(entry.getKey())) {
                result.put(entry.getKey(), entry.getValue());
            }
        }
        return result;
    }

    private boolean isJunk(String path) {
        String[] segments = path.split("/");
        for (String segment : segments) {
            if (JUNK_SEGMENTS.contains(segment)) {
                return true;
            }
        }
        String fileName = segments[segments.length - 1];
        return JUNK_FILE_NAMES.contains(fileName);
    }

    // ------------------------------------------------------------------
    // 步骤 4:剥顶层包裹目录——剥垃圾之后"顶层"这个词才有确定含义(业务建模文档 5.3)
    // ------------------------------------------------------------------

    /**
     * 判据写死,不用启发式猜:剥垃圾后若**所有**条目都在同一个顶层目录下,**且**该目录内直接含
     * {@code SKILL.md},则剥掉这一层;否则不剥,留给步骤 5 的结构校验去报错。
     */
    private Map<String, byte[]> stripTopLevelWrapper(Map<String, byte[]> withoutJunk) {
        if (withoutJunk.isEmpty()) {
            return withoutJunk;
        }
        String commonTopDir = null;
        for (String path : withoutJunk.keySet()) {
            int slashIndex = path.indexOf('/');
            if (slashIndex < 0) {
                // 已经有文件直接位于根,不可能存在统一的顶层包裹目录
                return withoutJunk;
            }
            String topSegment = path.substring(0, slashIndex);
            if (commonTopDir == null) {
                commonTopDir = topSegment;
            } else if (!commonTopDir.equals(topSegment)) {
                return withoutJunk;
            }
        }
        String wrapperSkillMdPath = commonTopDir + "/" + SKILL_MD;
        if (!withoutJunk.containsKey(wrapperSkillMdPath)) {
            return withoutJunk;
        }

        String prefix = commonTopDir + "/";
        Map<String, byte[]> stripped = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> entry : withoutJunk.entrySet()) {
            stripped.put(entry.getKey().substring(prefix.length()), entry.getValue());
        }
        return stripped;
    }

    // ------------------------------------------------------------------
    // 步骤 5:结构校验——只要求顶层有 SKILL.md,其余文件与目录随便叫什么
    // 目录深度基准:剥壳之后的相对路径,以路径分隔符个数为准
    // ------------------------------------------------------------------

    /**
     * <b>刻意不做顶层白名单。</b>曾经这里只放行 {@code SKILL.md / README.md / references|scripts|assets/},
     * 结果是真实世界的 skill 几乎全被拒:GitHub 上的包普遍带 {@code LICENSE}、{@code README.zh-CN.md}、
     * {@code docs/}、{@code templates/}、{@code examples/}。
     *
     * <p>去掉它有两条理由:
     *
     * <ol>
     *   <li><b>官方规范里没有这条约束。</b>Agent Skills 只要求 {@code SKILL.md} 存在,其余是任意
     *       supporting files —— 官方文档给的目录示例本身就把 {@code FORMS.md} / {@code reference.md} /
     *       {@code examples.md} 直接摆在顶层,目录还叫 {@code reference/} 而不是 {@code references/}。
     *       我们按一个更窄的解读去卡,卡掉的是合规的包。
     *   <li><b>它挡不住任何攻击,所以不是安全边界。</b>攻击者把恶意脚本放进 {@code scripts/} 就能绕过,
     *       白名单一个字都不用改。真正的安全边界是 zip-slip(见 {@code assessEntryPath})、解压上限、
     *       符号链接拒绝 —— 那三条与顶层条目叫什么名字完全无关。
     * </ol>
     *
     * <p>换句话说,这条规则的全部收益是"包看起来整洁",代价是平台装不了真实的 skill。而流通性正是这个
     * 平台唯一的价值,所以这笔交易怎么算都不划算。
     */
    private void validateStructure(Map<String, byte[]> canonical) {
        if (!canonical.containsKey(SKILL_MD)) {
            throw invalid(CODE_STRUCTURE_INVALID, "包内缺少顶层 SKILL.md");
        }
        for (String path : canonical.keySet()) {
            int depth = countSlashes(path);
            if (depth > limits.maxPathDepth()) {
                throw invalid(CODE_PATH_TOO_DEEP, "目录深度超过上限 " + limits.maxPathDepth() + ": " + path);
            }
        }
    }

    private int countSlashes(String path) {
        int count = 0;
        for (int i = 0; i < path.length(); i++) {
            if (path.charAt(i) == '/') {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------
    // 步骤 6:解析 frontmatter -> name / description / allowed-tools,校验长度
    // ------------------------------------------------------------------

    private ParsedFrontmatterFields parseFrontmatter(byte[] skillMdBytes) {
        String content = new String(skillMdBytes, StandardCharsets.UTF_8);
        FrontmatterBlock block = locateFrontmatterBlock(content);

        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setCodePointLimit(FRONTMATTER_CODE_POINT_LIMIT);
        Object parsed;
        try {
            parsed = new Yaml(loaderOptions).load(block.yaml());
        } catch (YAMLException e) {
            throw invalid(CODE_FRONTMATTER_INVALID, "frontmatter 不是合法的 YAML: " + e.getMessage());
        }
        if (!(parsed instanceof Map<?, ?> map)) {
            throw invalid(CODE_FRONTMATTER_INVALID, "frontmatter 必须是一个 YAML 映射(key: value 形式)");
        }

        String name = requireStringField(map, "name");
        if (name.length() > MAX_RAW_NAME_LENGTH) {
            throw invalid(
                    CODE_FRONTMATTER_INVALID,
                    "name 长度不得超过 " + MAX_RAW_NAME_LENGTH + " 个字符(需要为短哈希后缀留出余量),实际为 "
                            + name.length());
        }
        // 触发 kebab-case 校验:不合法时 SkillName 自己抛 InvalidSkillNameException——同样是 BusinessException,
        // 映射成 400,只是 code 不同(SKILL_NAME_INVALID),前端仍按 code 分支,不需要在这里重复一遍正则。
        new SkillName(name);

        String description = requireStringField(map, "description");
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            throw invalid(
                    CODE_FRONTMATTER_INVALID,
                    "description 长度不得超过 " + MAX_DESCRIPTION_LENGTH + " 个字符,实际为 " + description.length());
        }

        List<String> allowedTools = parseAllowedTools(map.get("allowed-tools"));

        return new ParsedFrontmatterFields(name, description, allowedTools);
    }

    private String requireStringField(Map<?, ?> map, String key) {
        Object value = map.get(key);
        if (!(value instanceof String str) || str.isBlank()) {
            throw invalid(CODE_FRONTMATTER_INVALID, "frontmatter 缺少必填字段或字段类型不是字符串: " + key);
        }
        return str.trim();
    }

    /**
     * {@code allowed-tools} 允许缺席(业务建模文档待验证项 7:{@code .claude/skills/playwright/SKILL.md}
     * 就没有这个字段,解析器必须允许它不存在)。YAML 列表形态每个元素独立即是一条工具声明;标量形态才需要走
     * {@link AllowedToolsParser} 的"只在括号外逗号切分"规则。
     */
    private List<String> parseAllowedTools(Object rawValue) {
        if (rawValue == null) {
            return List.of();
        }
        if (rawValue instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof String str) || str.isBlank()) {
                    throw invalid(CODE_FRONTMATTER_INVALID, "allowed-tools 列表元素必须是非空字符串");
                }
                result.add(str.trim());
            }
            return List.copyOf(result);
        }
        if (rawValue instanceof String scalar) {
            return AllowedToolsParser.parse(scalar);
        }
        throw invalid(CODE_FRONTMATTER_INVALID, "allowed-tools 必须是字符串或字符串列表");
    }

    // ------------------------------------------------------------------
    // 步骤 7:算 content_digest——在改名之前,与压缩器实现无关(业务建模文档 3.7)
    // ------------------------------------------------------------------

    private String computeContentDigest(Map<String, byte[]> canonical) {
        // canonical 来自 stripTopLevelWrapper,内部实现始终是 LinkedHashMap 按插入顺序——这里显式按 key
        // 字典序重新遍历一遍而不是依赖调用方保证有序,是"定义决定实现"而不是"实现恰好满足定义"。
        List<String> sortedPaths = canonical.keySet().stream().sorted().toList();
        StringBuilder manifest = new StringBuilder();
        for (String path : sortedPaths) {
            manifest.append(path).append('\n').append(Sha256.hex(canonical.get(path))).append('\n');
        }
        return Sha256.hex(manifest.toString());
    }

    // ------------------------------------------------------------------
    // 步骤 9:改写 SKILL.md 的 frontmatter name——只改这一个键,其余内容一个字节都不动
    // ------------------------------------------------------------------

    private String rewriteFrontmatterName(String skillMdContent, String finalName) {
        FrontmatterBlock block = locateFrontmatterBlock(skillMdContent);
        String[] lines = splitLines(skillMdContent);

        boolean replaced = false;
        for (int i = block.firstDelimiterLineIndex() + 1; i < block.closingDelimiterLineIndex(); i++) {
            if (NAME_LINE_PATTERN.matcher(stripTrailingCr(lines[i])).matches()) {
                boolean hadCr = lines[i].endsWith("\r");
                lines[i] = "name: " + finalName + (hadCr ? "\r" : "");
                replaced = true;
                break;
            }
        }
        if (!replaced) {
            // 理论上不会走到这里:parseFrontmatter 已经校验过 name 字段存在。显式失败而不是静默追加一行,
            // 避免"重新打包出的内容其实没有真的改名"这种更隐蔽的 bug。
            throw invalid(CODE_FRONTMATTER_INVALID, "frontmatter 中未找到 name 字段,无法改写");
        }
        return String.join("\n", lines);
    }

    /**
     * 定位 frontmatter 的边界(首尾两行单独的 {@code ---}),{@link #parseFrontmatter} 与
     * {@link #rewriteFrontmatterName} 共用同一套定位规则,避免两处对"frontmatter 到底在哪"各写一份可能不一致
     * 的判断逻辑。
     */
    private FrontmatterBlock locateFrontmatterBlock(String content) {
        String[] lines = splitLines(content);
        if (lines.length == 0 || !stripTrailingCr(lines[0]).equals(FRONTMATTER_DELIMITER)) {
            throw invalid(CODE_FRONTMATTER_INVALID, "SKILL.md 必须以单独一行 --- 开始 frontmatter");
        }
        for (int i = 1; i < lines.length; i++) {
            if (stripTrailingCr(lines[i]).equals(FRONTMATTER_DELIMITER)) {
                String yaml = String.join("\n", Arrays.copyOfRange(lines, 1, i));
                return new FrontmatterBlock(yaml, 0, i);
            }
        }
        throw invalid(CODE_FRONTMATTER_INVALID, "SKILL.md 缺少 frontmatter 结束标记 ---");
    }

    private String[] splitLines(String content) {
        return content.split("\n", -1);
    }

    private String stripTrailingCr(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }

    // ------------------------------------------------------------------
    // 步骤 10:按最终 name 重新打包,顶层目录 = 最终 name
    // ------------------------------------------------------------------

    private byte[] buildZip(Map<String, byte[]> files, String rewrittenSkillMd, String finalName) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(buffer, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, byte[]> entry : files.entrySet()) {
                String relativePath = entry.getKey();
                byte[] content = relativePath.equals(SKILL_MD)
                        ? rewrittenSkillMd.getBytes(StandardCharsets.UTF_8)
                        : entry.getValue();
                zos.putNextEntry(new ZipEntry(finalName + "/" + relativePath));
                zos.write(content);
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("重新打包 skill 制品失败, finalName=" + finalName, e);
        }
        return buffer.toByteArray();
    }

    private SkillPackageInvalidException invalid(String code, String message) {
        return new SkillPackageInvalidException(code, message);
    }

    /** frontmatter 解析出的三个字段,内部中转类型,不暴露给调用方(调用方拿到的是 {@link ParsedSkillPackage})。 */
    private record ParsedFrontmatterFields(String name, String description, List<String> allowedTools) {}

    /**
     * @param yaml 首尾分隔符之间的 YAML 文本
     * @param firstDelimiterLineIndex 首个 {@code ---} 所在行号(恒为 0)
     * @param closingDelimiterLineIndex 结束 {@code ---} 所在行号
     */
    private record FrontmatterBlock(String yaml, int firstDelimiterLineIndex, int closingDelimiterLineIndex) {}
}
