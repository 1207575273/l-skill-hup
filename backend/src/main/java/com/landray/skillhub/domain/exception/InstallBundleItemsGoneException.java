package com.landray.skillhub.domain.exception;

import com.landray.skillhub.shared.exception.BusinessException;

/**
 * 安装单里的 skill 全部已被物理删除。
 *
 * <p>业务建模文档 4.8 原本以"skill 一旦被人装过就有了外部引用,物理删除会让已流传的安装命令报出难以理解的错误"
 * 为由拒绝物理删除。删除能力按需求加上了,那个理由并没有因此消失 —— 这个异常就是正面处理它的地方。
 *
 * <p>不复用 {@code ResourceNotFoundException}(404):安装单本身是存在的、token 也没错、也没过期,404 会把用户
 * 引向"是不是链接抄错了"这条完全错误的排查路径。用 410 Gone 与 {@link InstallBundleExpiredException} 对齐:
 * 都是"这个资源曾经有效,现在不再有效",而正确的下一步都是"回平台重新生成"。
 *
 * <p>部分条目被删不走这里 —— 那种情况仍然正常安装剩下的,只是在脚本里打印一行跳过提示。只有一个都不剩时,
 * 生成一个什么都不装的脚本才是真正的误导。
 */
public class InstallBundleItemsGoneException extends BusinessException {

    private static final String CODE = "INSTALL_BUNDLE_ITEMS_GONE";
    private static final int HTTP_STATUS_GONE = 410;

    public InstallBundleItemsGoneException() {
        super(CODE, "该安装单里的 skill 均已被删除,请回平台重新生成安装命令", HTTP_STATUS_GONE);
    }
}
