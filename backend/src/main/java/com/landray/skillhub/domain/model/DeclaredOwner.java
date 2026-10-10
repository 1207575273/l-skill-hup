package com.landray.skillhub.domain.model;

/**
 * 匿名上传时上传者**自称**的归属人,没有账号背书。
 *
 * <p><b>为什么不复用 {@link SkillOwner}</b>:那个类型描述的是 {@code owner_user_id} 指向的真实账号 ——
 * 它的 {@code userId} 能拿去查人、能用来判权限。这里的姓名和工号是一段自由文本,谁都能填任何人的名字,
 * 不能拿去做任何判断。两者混成一个类型,调用方就没法在类型层面知道手上这份归属信息可不可信;而这个项目
 * 分发的是会被 Agent 执行的内容,"这个包是谁的"可信与否是使用者的核心判断依据之一,不能含糊。
 *
 * <p><b>工号为什么也要留一格</b>:同名同姓在公司规模上是必然事件(业务建模文档 3.3),只给姓名等于没给线索。
 * 它同样未经核实,只是让认领环节与旁人判断时多一个可对照的信息,**不作为认领资格的门槛**(本地建号普遍
 * 没绑工号,拿它当门槛会导致声明了工号的包谁都认领不了)。
 *
 * <p>两个字段都允许为空:匿名上传时什么都不填是合法的,那种情况整个 {@code DeclaredOwner} 为 {@code null},
 * 而不是一个两格都空的实例 —— "没有声明"与"声明了空"不该是两种状态。
 *
 * @param name 自称的归属人姓名,上限与 {@code app_user.display_name} 一致(64)
 * @param employeeNo 自称的工号,可空,上限与 {@code app_user.employee_no} 一致(32)
 */
public record DeclaredOwner(String name, String employeeNo) {

    /** 与 {@code skill.declared_owner_name} 列宽一致,也与 {@link AppUser#MAX_DISPLAY_NAME_LENGTH} 一致。 */
    public static final int MAX_NAME_LENGTH = 64;

    /** 与 {@code skill.declared_owner_employee_no} 列宽一致。 */
    public static final int MAX_EMPLOYEE_NO_LENGTH = 32;

    public DeclaredOwner {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("声明归属的姓名不能为空 —— 不填归属时整个 DeclaredOwner 应为 null");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException(
                    "声明归属的姓名不得超过 " + MAX_NAME_LENGTH + " 个字符,实际 " + name.length());
        }
        if (employeeNo != null && employeeNo.length() > MAX_EMPLOYEE_NO_LENGTH) {
            throw new IllegalArgumentException(
                    "声明归属的工号不得超过 " + MAX_EMPLOYEE_NO_LENGTH + " 个字符,实际 " + employeeNo.length());
        }
    }

    /**
     * 从可能为空白的原始输入构造,全空白时返回 {@code null}。
     *
     * <p>入口在 interfaces 层收到的是两个可选的表单字段,"都没填"和"填了空格"都应该落到"没有声明"这一个状态。
     * 把这个归一化放在领域类型自己身上,而不是让每个调用方各写一遍 {@code isBlank} 判断。
     */
    public static DeclaredOwner ofNullable(String rawName, String rawEmployeeNo) {
        String name = trimToNull(rawName);
        if (name == null) {
            // 只填工号不填姓名:视为没有声明。一个光秃秃的工号对旁人判断"这是谁的包"没有帮助,
            // 却会让前端多出一种要特判的半残状态。
            return null;
        }
        return new DeclaredOwner(name, trimToNull(rawEmployeeNo));
    }

    private static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
