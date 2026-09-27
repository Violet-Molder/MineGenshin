package com.linweiyun.genshin.client.render.gui.component;

import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.integration.kjs.KJSBindings;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.network.chat.Component;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@KJSBindings
@LDLRegister(name = "custom-toggle", group = "minegenshin", registry = "ldlib2:ui_element")
public class CustomToggle extends Toggle {

    private final TextElement buttonText;

    public CustomToggle() {
        super();

        // 布局：横向居中，无内边距，支持百分比尺寸
        this.getLayout()
                .flexDirection(FlexDirection.ROW)
                .alignItems(AlignItems.CENTER)
                .paddingAll(0.0F);

        // 隐藏默认的侧边 Label
        this.noText();

        // 隐藏勾选标记图标（按钮式 Toggle 不需要勾选样式）
        this.markIcon.getLayout()
                .widthPercent(100.0F)
                .heightPercent(100.0F);

        // 配置 toggleButton：撑满整个 CustomToggle
        this.toggleButton
                .layout(layout -> {
                    layout.paddingAll(0.0F);
                    layout.widthPercent(100.0F);
                    layout.heightPercent(100.0F);
                });

        // 隐藏默认按钮文本（我们用自定义的 TextElement）
        this.toggleButton.noText();

        // 创建自定义文本：居中显示
        buttonText = new TextElement();
        // 给这层文字一个稳定的类名：文字色得写在「文字元素」自己身上（LDLib2 的样式不做继承，
        // 写在 CustomToggle 根上的 text-color 到不了这里）。按钮内部文字的配色就靠它
        // （例如角色配置页那两个页签的「选中 / 未选中」两档色）。
        buttonText.addClass("__custom_toggle_text__");
        buttonText.getLayout()
                .widthPercent(100.0F)
                .heightPercent(100.0F)
                .positionType(TaffyPosition.ABSOLUTE);
        buttonText.getTextStyle()
                .textAlignHorizontal(Horizontal.CENTER)
                .textAlignVertical(Vertical.CENTER);
        buttonText.getStyle().zIndex(2);

        this.toggleButton.addChild(buttonText);
    }

    /**
     * 设置按钮显示的文本
     */
    public CustomToggle setButtonText(String text) {
        return setButtonText(Component.literal(text));
    }

    /**
     * 设置按钮显示的文本
     */
    public CustomToggle setButtonText(Component text) {
        this.buttonText.setText(text);
        return this;
    }

    /**
     * 获取当前按钮文本
     */
    public Component getButtonText() {
        return this.buttonText.getText();
    }
}
