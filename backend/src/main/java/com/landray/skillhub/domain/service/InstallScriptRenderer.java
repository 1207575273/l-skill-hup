package com.landray.skillhub.domain.service;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import com.landray.skillhub.domain.model.InstallBundle;
import com.landray.skillhub.domain.model.InstallScope;
import com.landray.skillhub.domain.model.SkillArtifact;
import com.landray.skillhub.domain.model.SkillName;

/**
 * 把一个 {@link InstallBundle} 渲染成 PowerShell / bash 安装脚本(业务建模文档 5.9)。
 *
 * <p>纯 Java 领域服务:输入是已经 join 好的 {@link SkillArtifact} 列表(哪个版本、认证状态是否 {@code REVOKED}、
 * skill 是否 {@code UNLISTED} 全部在调用方渲染前实时查出),输出是完整的脚本文本,不做任何 I/O——跨越
 * {@code InstallBundle} / {@code SkillArtifact} / {@code ClientCode} / {@code Certification} 多个实体的规则
 * 只在这里表达一次,符合 backend/CLAUDE.md 第二节"领域服务只在业务确实跨实体时才建"的判定。
 *
 * <p><b>三条安全约束在这里落地(根 CLAUDE.md 第七节 + 业务建模文档 5.9):</b>
 *
 * <ol>
 *   <li>{@code REVOKED} 的版本:打印警示 + 撤销理由,并生成"默认中止、需要 {@code -Force}/{@code --force}
 *       才继续"的逻辑——这段逻辑必须写进脚本本身,因为脚本下载后是在用户本机离线执行的,服务端此刻早已不在场。
 *   <li>sha256 在下载后立即校验,不匹配就地中止,不吞进下一步。
 *   <li>目标目录只能是 {@code <skills 根目录>/<skillName>},{@code skillName} 复用 {@link SkillName} 的
 *       kebab-case 校验再次把关——它将被拼进本机文件系统路径,不允许任何不受控的片段流入。同名目录覆盖前
 *       先备份成 {@code <name>.bak.<时间戳>}。
 * </ol>
 *
 * <p><b>{@code .sh} 全文只用 {@code \n},不出现任何 {@code \r}</b>——CRLF 会让 sh 报 {@code bad interpreter:
 * /bin/bash^M}(根 CLAUDE.md 踩坑点 8.8),且这个报错完全看不出是行尾问题。Java 文本块(text block)本身的换行在
 * 编译期就固定为 {@code \n}(JLS 规范行为,与源文件在磁盘上的行尾设置无关),{@link #sanitizeFreeText} 额外剥离
 * {@code certifyNote} 里可能混入的 {@code \r},两道防线叠加,保证不会有第二个来源引入 {@code \r}。
 */
public final class InstallScriptRenderer {

    private static final Pattern SAFE_VERSION_PATTERN = Pattern.compile("^[0-9][0-9A-Za-z.+-]*$");

    private InstallScriptRenderer() {}

    public static String renderPowerShell(
            InstallBundle bundle, List<SkillArtifact> artifacts, String publicBaseUrl, int deletedItemCount) {
        requireNonEmpty(artifacts);
        StringBuilder script = new StringBuilder();
        script.append("# skill-hub 安装脚本(自动生成,请勿分享超出团队范围)\n");
        script.append("# 生成时间: ").append(bundle.createdAt()).append('\n');
        script.append("# 过期时间: ").append(bundle.expiresAt()).append('\n');
        script.append("param(\n");
        script.append("    [switch]$Force,\n");
        script.append("    [switch]$Yes\n");
        script.append(")\n\n");
        script.append("$ErrorActionPreference = 'Stop'\n\n");
        script.append(powerShellSkillsRoot(bundle)).append('\n');
        script.append("Write-Host (\"目标目录: {0}\" -f $SkillsRoot)\n\n");
        // 警示与撤销闸门必须排在确认提示之前。否则带 -Force 运行时,用户是先按下 y 确认、之后才看到
        // "这个 skill 的认证已被撤销" —— 那次确认就是不知情的,而认证撤销恰恰是唯一覆盖内容语义的信号。
        appendRevokedGuardPowerShell(script, artifacts);
        appendUnlistedNoticesPowerShell(script, artifacts);
        appendDeletedNoticePowerShell(script, deletedItemCount);

        script.append("if (-not $Yes) {\n");
        script.append("    $confirm = Read-Host '确认安装以上 skill 到该目录? [y/N]'\n");
        script.append("    if ($confirm -ne 'y' -and $confirm -ne 'Y') {\n");
        script.append("        Write-Host '已取消安装'\n");
        script.append("        exit 0\n");
        script.append("    }\n");
        script.append("}\n\n");

        script.append("New-Item -ItemType Directory -Force -Path $SkillsRoot | Out-Null\n\n");
        script.append(POWERSHELL_INSTALL_FUNCTION).append("\n\n");

        for (SkillArtifact artifact : artifacts) {
            script.append("Install-OneSkill -Name ")
                    .append(quotePs(safeSkillName(artifact)))
                    .append(" -Version ")
                    .append(quotePs(safeVersion(artifact)))
                    .append(" -Sha256 ")
                    .append(quotePs(lowerCaseSha256(artifact)))
                    .append(" -DownloadUrl ")
                    .append(quotePs(downloadUrl(publicBaseUrl, artifact)))
                    .append('\n');
        }

        script.append("\nWrite-Host '全部安装完成' -ForegroundColor Green\n");
        return script.toString();
    }

    public static String renderBash(
            InstallBundle bundle, List<SkillArtifact> artifacts, String publicBaseUrl, int deletedItemCount) {
        requireNonEmpty(artifacts);
        StringBuilder script = new StringBuilder();
        script.append("#!/usr/bin/env bash\n");
        script.append("# skill-hub 安装脚本(自动生成,请勿分享超出团队范围)\n");
        script.append("# 生成时间: ").append(bundle.createdAt()).append('\n');
        script.append("# 过期时间: ").append(bundle.expiresAt()).append('\n');
        script.append("set -euo pipefail\n\n");
        script.append("FORCE=0\n");
        script.append("YES=0\n");
        script.append("for arg in \"$@\"; do\n");
        script.append("  case \"$arg\" in\n");
        script.append("    --force) FORCE=1 ;;\n");
        script.append("    -y|--yes) YES=1 ;;\n");
        script.append("  esac\n");
        script.append("done\n\n");
        script.append(bashSkillsRoot(bundle)).append('\n');
        script.append("echo \"目标目录: $SKILLS_ROOT\"\n\n");
        // 顺序理由同 renderPowerShell:确认必须是知情的确认。
        appendRevokedGuardBash(script, artifacts);
        appendUnlistedNoticesBash(script, artifacts);
        appendDeletedNoticeBash(script, deletedItemCount);

        // 确认必须从 /dev/tty 读,不能从 stdin 读。
        //
        // 文档给的标准用法是 `curl -fsSL <url> | sh`,这时脚本的 stdin 是 curl 的管道 ——
        // `read` 立刻拿到 EOF,confirm 恒为空,于是**每一次都走"已取消安装"**,用户根本没有机会按 y。
        // 实测就是这个症状,而且极具误导性:看起来像是脚本认为你拒绝了,其实是它压根没问。
        //
        // PowerShell 侧没有这个问题,`irm | iex` 只是把字符串当代码执行,stdin 仍是终端,
        // 所以那边的 Read-Host 照常工作 —— 两边行为不一致正是这个 bug 难被发现的原因。
        //
        // 提示也写到 /dev/tty:stdout 可能被重定向,那时提示会跟着进文件,用户对着空白光标不知道在等什么。
        script.append("if [ \"$YES\" -ne 1 ]; then\n");
        script.append("  if [ -e /dev/tty ]; then\n");
        script.append("    printf '确认安装以上 skill 到该目录? [y/N] ' > /dev/tty\n");
        script.append("    read -r confirm < /dev/tty\n");
        script.append("  else\n");
        // 没有终端是**装不了**,不是"用户取消",所以 exit 1。静默 exit 0 会让 CI 里安装没发生却当成功。
        // 顺带把正确写法直接给出来:`curl | sh` 传不进参数(sh 会把 --yes 当文件名),
        // 必须写成 `sh -s -- --yes`,不提示没人猜得到。
        script.append("    echo '当前环境没有终端,无法交互确认。'\n");
        script.append("    echo '如确认安装,改用: curl -fsSL <本脚本地址> | sh -s -- --yes'\n");
        script.append("    exit 1\n");
        script.append("  fi\n");
        script.append("  if [ \"$confirm\" != 'y' ] && [ \"$confirm\" != 'Y' ]; then\n");
        script.append("    echo '已取消安装'\n");
        script.append("    exit 0\n");
        script.append("  fi\n");
        script.append("fi\n\n");

        script.append("mkdir -p \"$SKILLS_ROOT\"\n\n");
        script.append(BASH_INSTALL_FUNCTION).append("\n\n");

        for (SkillArtifact artifact : artifacts) {
            script.append("install_one_skill ")
                    .append(quoteSh(safeSkillName(artifact)))
                    .append(' ')
                    .append(quoteSh(safeVersion(artifact)))
                    .append(' ')
                    .append(quoteSh(lowerCaseSha256(artifact)))
                    .append(' ')
                    .append(quoteSh(downloadUrl(publicBaseUrl, artifact)))
                    .append('\n');
        }

        script.append("\necho '全部安装完成'\n");
        return script.toString();
    }

    private static final String POWERSHELL_INSTALL_FUNCTION =
            """
            function Install-OneSkill {
                param($Name, $Version, $Sha256, $DownloadUrl)

                # 后缀必须是 .zip:Expand-Archive 按扩展名判断格式,拿到 .tmp 会直接报
                # ".tmp 不是支持的存档文件格式"。[System.IO.Path]::GetTempFileName() 给的正是 .tmp,
                # 所以这里自己拼路径而不是用它。顺带避开它的两个毛病:它会真的创建一个空文件
                # (紧接着就被 -OutFile 覆盖,白做一次 IO),以及 TEMP 目录里堆到 65535 个 tmp 文件时会抛异常。
                $tempFile = Join-Path ([System.IO.Path]::GetTempPath()) ("skillhub-" + [guid]::NewGuid().ToString('N') + ".zip")
                try {
                    Invoke-WebRequest -Uri $DownloadUrl -OutFile $tempFile -UseBasicParsing

                    $actualHash = (Get-FileHash -Path $tempFile -Algorithm SHA256).Hash.ToLower()
                    if ($actualHash -ne $Sha256) {
                        Write-Host "sha256 校验失败,已中止安装: $Name (期望 $Sha256, 实际 $actualHash)" -ForegroundColor Red
                        exit 1
                    }

                    $targetDir = Join-Path $SkillsRoot $Name
                    if (Test-Path $targetDir) {
                        $backupDir = "$targetDir.bak.$(Get-Date -Format 'yyyyMMddHHmmss')"
                        Write-Host "目标目录已存在,备份为: $backupDir"
                        Move-Item -Path $targetDir -Destination $backupDir -Force
                    }

                    # 解压到 $SkillsRoot 而不是 $targetDir,理由同 bash 侧:zip 顶层目录就是 skill name,
                    # 解到 $targetDir 会套成 skills/foo/foo/SKILL.md。
                    Expand-Archive -Path $tempFile -DestinationPath $SkillsRoot -Force
                    Write-Host "已安装: $Name@$Version -> $targetDir" -ForegroundColor Green
                } finally {
                    Remove-Item -Path $tempFile -ErrorAction SilentlyContinue
                }
            }""";

    private static final String BASH_INSTALL_FUNCTION =
            """
            install_one_skill() {
              local name="$1" version="$2" expected_sha256="$3" download_url="$4"
              local tmp_file
              tmp_file="$(mktemp)"
              trap 'rm -f "$tmp_file"' RETURN

              curl -fsSL -o "$tmp_file" "$download_url"

              local actual_sha256
              actual_sha256="$(sha256sum "$tmp_file" | awk '{print $1}')"
              if [ "$actual_sha256" != "$expected_sha256" ]; then
                echo "sha256 校验失败,已中止安装: $name (期望 $expected_sha256, 实际 $actual_sha256)"
                exit 1
              fi

              local target_dir="$SKILLS_ROOT/$name"
              if [ -e "$target_dir" ]; then
                local backup_dir
                backup_dir="$target_dir.bak.$(date +%Y%m%d%H%M%S)"
                echo "目标目录已存在,备份为: $backup_dir"
                mv "$target_dir" "$backup_dir"
              fi

              # 解压到 SKILLS_ROOT 而不是 target_dir:制品 zip 的顶层目录**就是 skill name**
              # (5.3 第 10 步:按最终 name 打包,顶层目录 = 最终 name),解到 target_dir 会套成
              # skills/foo/foo/SKILL.md,而那样 Agent 找不到 SKILL.md,等于没装上。
              # 也不需要 mkdir target_dir —— unzip 会按 zip 里的目录结构自己建。
              mkdir -p "$SKILLS_ROOT"
              unzip -q "$tmp_file" -d "$SKILLS_ROOT"
              echo "已安装: $name@$version -> $target_dir"
            }""";

    private static void appendRevokedGuardPowerShell(StringBuilder script, List<SkillArtifact> artifacts) {
        List<SkillArtifact> revoked = artifacts.stream().filter(SkillArtifact::isRevoked).toList();
        if (revoked.isEmpty()) {
            return;
        }
        script.append("$HasRevoked = $true\n");
        for (SkillArtifact artifact : revoked) {
            script.append("Write-Host ")
                    .append(quotePs("警告: skill '" + safeSkillName(artifact) + "' 版本 " + safeVersion(artifact)
                            + " 的平台认证已被撤销"))
                    .append(" -ForegroundColor Red\n");
            String note = sanitizeFreeText(artifact.certifyNote());
            if (!note.isBlank()) {
                script.append("Write-Host ").append(quotePs("撤销理由: " + note)).append(" -ForegroundColor Red\n");
            }
        }
        script.append("if ($HasRevoked -and -not $Force) {\n");
        script.append("    Write-Host '检测到已撤销认证的 skill,已中止安装。"
                + "如确认需要安装,请附加 -Force 参数重新运行本脚本。' -ForegroundColor Red\n");
        script.append("    exit 1\n");
        script.append("}\n\n");
    }

    private static void appendUnlistedNoticesPowerShell(StringBuilder script, List<SkillArtifact> artifacts) {
        List<SkillArtifact> unlisted = artifacts.stream().filter(SkillArtifact::isUnlisted).toList();
        for (SkillArtifact artifact : unlisted) {
            script.append("Write-Host ")
                    .append(quotePs("提示: skill '" + safeSkillName(artifact) + "' 已下架,仍可正常安装该已生成的版本"))
                    .append(" -ForegroundColor Yellow\n");
        }
        if (!unlisted.isEmpty()) {
            script.append('\n');
        }
    }

    /**
     * 安装单里有条目在生成之后被物理删除时,明说跳过了几个。
     *
     * <p><b>不能静默少装。</b>安装单的语义是"把我勾的这几个装上",少装一个而什么都不说,用户会以为装全了,
     * 直到某次 Agent 找不到那个 skill 才发现 —— 那时已经完全对不上原因了。这正是业务建模文档 4.8 当初拒绝
     * 物理删除的顾虑,删除能力加上之后,这里就是兑现那份顾虑的地方。
     *
     * <p>只说数量不说名字:名字随 skill 行一起删掉了,查不到。硬要从别处翻出来展示反而会给人"它还在"的错觉。
     */
    private static void appendDeletedNoticePowerShell(StringBuilder script, int deletedItemCount) {
        if (deletedItemCount <= 0) {
            return;
        }
        script.append("Write-Host ")
                .append(quotePs("提示: 本安装单中有 " + deletedItemCount + " 个 skill 已从平台删除,将跳过它们"))
                .append(" -ForegroundColor Yellow\n\n");
    }

    /** 见 {@link #appendDeletedNoticePowerShell}。 */
    private static void appendDeletedNoticeBash(StringBuilder script, int deletedItemCount) {
        if (deletedItemCount <= 0) {
            return;
        }
        script.append("echo ")
                .append(quoteSh("提示: 本安装单中有 " + deletedItemCount + " 个 skill 已从平台删除,将跳过它们"))
                .append("\n\n");
    }

    private static void appendRevokedGuardBash(StringBuilder script, List<SkillArtifact> artifacts) {
        List<SkillArtifact> revoked = artifacts.stream().filter(SkillArtifact::isRevoked).toList();
        if (revoked.isEmpty()) {
            return;
        }
        script.append("HAS_REVOKED=1\n");
        for (SkillArtifact artifact : revoked) {
            script.append("echo ")
                    .append(quoteSh("警告: skill '" + safeSkillName(artifact) + "' 版本 " + safeVersion(artifact)
                            + " 的平台认证已被撤销"))
                    .append('\n');
            String note = sanitizeFreeText(artifact.certifyNote());
            if (!note.isBlank()) {
                script.append("echo ").append(quoteSh("撤销理由: " + note)).append('\n');
            }
        }
        script.append("if [ \"$HAS_REVOKED\" -eq 1 ] && [ \"$FORCE\" -ne 1 ]; then\n");
        script.append("  echo '检测到已撤销认证的 skill,已中止安装。"
                + "如确认需要安装,请附加 --force 参数重新运行本脚本。'\n");
        script.append("  exit 1\n");
        script.append("fi\n\n");
    }

    private static void appendUnlistedNoticesBash(StringBuilder script, List<SkillArtifact> artifacts) {
        List<SkillArtifact> unlisted = artifacts.stream().filter(SkillArtifact::isUnlisted).toList();
        for (SkillArtifact artifact : unlisted) {
            script.append("echo ")
                    .append(quoteSh("提示: skill '" + safeSkillName(artifact) + "' 已下架,仍可正常安装该已生成的版本"))
                    .append('\n');
        }
        if (!unlisted.isEmpty()) {
            script.append('\n');
        }
    }

    private static String powerShellSkillsRoot(InstallBundle bundle) {
        String subDir = bundle.client().skillsSubDir();
        if (bundle.scope() == InstallScope.PROJECT) {
            return "$SkillsRoot = Join-Path (Get-Location).Path '" + subDir + "'";
        }
        return "$SkillsRoot = Join-Path $HOME '" + subDir + "'";
    }

    private static String bashSkillsRoot(InstallBundle bundle) {
        String subDir = bundle.client().skillsSubDir();
        if (bundle.scope() == InstallScope.PROJECT) {
            return "SKILLS_ROOT=\"$(pwd)/" + subDir + "\"";
        }
        return "SKILLS_ROOT=\"$HOME/" + subDir + "\"";
    }

    private static String downloadUrl(String publicBaseUrl, SkillArtifact artifact) {
        String base = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1)
                : publicBaseUrl;
        return base + "/api/v1/skills/" + artifact.skillId() + "/versions/" + safeVersion(artifact) + "/download";
    }

    private static String lowerCaseSha256(SkillArtifact artifact) {
        return artifact.sha256().toLowerCase(Locale.ROOT);
    }

    /**
     * 复用 {@link SkillName} 的 kebab-case 校验作为渲染前的最后一道防线——{@code skillName} 将被拼进本机文件系统
     * 路径({@code <skills 根目录>/<skillName>}),数据来自数据库,理论上早已在写入路径被校验过,这里属于纵深
     * 防御:任何一处上游校验被绕过或存在历史脏数据,都不能让不安全的片段流进渲染出的脚本。
     */
    private static String safeSkillName(SkillArtifact artifact) {
        return new SkillName(artifact.skillName()).value();
    }

    private static String safeVersion(SkillArtifact artifact) {
        String version = artifact.version();
        if (version == null || !SAFE_VERSION_PATTERN.matcher(version).matches()) {
            throw new IllegalStateException("version 不满足安全字符集,拒绝渲染安装脚本, version=" + version);
        }
        return version;
    }

    private static String quotePs(String raw) {
        return "'" + raw.replace("'", "''") + "'";
    }

    private static String quoteSh(String raw) {
        return "'" + raw.replace("'", "'\"'\"'") + "'";
    }

    /**
     * 剥离 {@code certifyNote} 里可能混入的控制字符(含 {@code \r} {@code \n} {@code \t}),折叠成单个空格。
     *
     * <p>这不是为了防脚本注入——单引号包裹的字符串在 PowerShell 与 POSIX shell 里都允许内嵌字面换行而不会
     * 提前闭合引号,真正需要转义的只有引号本身({@link #quotePs}/{@link #quoteSh} 已处理)。这里剥离控制字符
     * 有两个独立理由:一是维持 {@code .sh} 全文不出现 {@code \r} 的硬约束(根 CLAUDE.md 踩坑点 8.8);
     * 二是 backend/CLAUDE.md 6.5 的通用要求——用户可控字符串进入任何输出前都要去掉控制字符,避免伪造换行。
     */
    private static String sanitizeFreeText(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder sanitized = new StringBuilder(raw.length());
        boolean lastWasSpace = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (Character.isISOControl(c)) {
                if (!lastWasSpace) {
                    sanitized.append(' ');
                    lastWasSpace = true;
                }
                continue;
            }
            sanitized.append(c);
            lastWasSpace = false;
        }
        return sanitized.toString().trim();
    }

    private static void requireNonEmpty(List<SkillArtifact> artifacts) {
        if (artifacts == null || artifacts.isEmpty()) {
            throw new IllegalStateException("渲染安装脚本时制品列表不能为空");
        }
    }
}
