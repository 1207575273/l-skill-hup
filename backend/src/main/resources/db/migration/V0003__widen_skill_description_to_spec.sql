-- 把 skill.description 从 VARCHAR(512) 放宽到 VARCHAR(1024),与 Agent Skills 官方规范对齐。
--
-- 【为什么改】
-- 512 这个数当初是我们自己定的,没有依据。官方规范对 SKILL.md frontmatter 的约束是:
--   name        最多 64 字符,小写字母/数字/连字符,不含 "anthropic" / "claude"
--   description 非空,最多 1024 字符
-- 实测从 GitHub 拖一个真实 skill 进来,description 长度 616,直接被我们的 512 拒收 ——
-- 包是合规的,拒错了。
--
-- 【教训,值得记一笔】
-- 原来的列注释写的是"上限 512 字符,超长在校验阶段拒绝",听起来像业务规则,其实是**先定了列宽、
-- 再让校验去迁就列宽**。方向反了:这个字段的上限由外部规范决定,数据库只是存它的地方。
-- 凡是"规范说了算"的字段,列宽必须跟着规范走,不能反过来让规范迁就存储。
--
-- 【安全性】
-- 加宽 VARCHAR 不重写表数据、不需要 ACCESS EXCLUSIVE 之外的额外代价,PostgreSQL 对
-- "VARCHAR(n) -> VARCHAR(m) 且 m > n" 有专门优化:只改 catalog,不做全表校验。
-- 现有数据全部满足新约束(旧数据本就 <= 512),不会有行被拒。

ALTER TABLE skill ALTER COLUMN description TYPE VARCHAR(1024);

COMMENT ON COLUMN skill.description IS
    '一句话描述,来自 SKILL.md frontmatter。上限 1024 字符,与 Agent Skills 官方规范一致;'
        '超长在校验阶段拒绝 —— 不能让一段 10KB 的描述进列表页响应';
