package litematica.gui.widget;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import javax.annotation.Nullable;

import malilib.gui.BaseScreen;
import malilib.gui.icon.BaseIcon;
import malilib.gui.icon.DefaultIcons;
import malilib.gui.widget.ContainerWidget;
import malilib.gui.widget.IconWidget;
import malilib.gui.widget.LabelWidget;
import malilib.gui.widget.button.GenericButton;
import malilib.render.text.StyledText;
import malilib.render.text.StyledTextLine;
import malilib.render.text.StyledTextUtils;
import malilib.util.StringUtils;
import malilib.util.game.MinecraftVersion;
import malilib.util.position.Vec3i;
import litematica.config.Configs;
import litematica.gui.SchematicInfoConfigScreen;
import litematica.gui.util.AbstractSchematicInfoCache;
import litematica.gui.util.AbstractSchematicInfoCache.SchematicInfo;
import litematica.schematic.SchematicMetadata;

public abstract class AbstractSchematicInfoWidget<T> extends ContainerWidget
{
    protected final AbstractSchematicInfoCache<T> infoCache;
    protected final GenericButton configButton;
    protected final LabelWidget descriptionLabel;
    protected final LabelWidget infoTextLabel;
    protected final IconWidget iconWidget;
    @Nullable protected SchematicInfo currentInfo;
    protected boolean hasDescription;

    public AbstractSchematicInfoWidget(int width, int height, AbstractSchematicInfoCache<T> cache)
    {
        super(width, height);

        this.infoCache = cache;
        this.configButton = GenericButton.create(DefaultIcons.INFO_11, this::openConfigScreen);

        this.infoTextLabel = new LabelWidget();
        this.infoTextLabel.setLineHeight(12);

        this.descriptionLabel = new LabelWidget();
        this.descriptionLabel.setLineHeight(12);

        this.iconWidget = new IconWidget(null);
        this.iconWidget.getBorderRenderer().getNormalSettings().setBorderWidthAndColor(1, 0xFFC0C0C0);
        // FIXME (malilib) this seems silly to have to be enabled for the border to render (see IconWidget#renderAt())
        this.iconWidget.getBackgroundRenderer().getNormalSettings().setEnabled(true);

        this.getBackgroundRenderer().getNormalSettings().setEnabledAndColor(true, 0xC0000000);
        this.getBorderRenderer().getNormalSettings().setBorderWidthAndColor(1, 0xFFC0C0C0);
    }

    @Override
    public void reAddSubWidgets()
    {
        super.reAddSubWidgets();

        this.addWidget(this.configButton);

        if (this.currentInfo == null)
        {
            return;
        }

        this.addWidget(this.infoTextLabel);
        this.addWidgetIf(this.descriptionLabel, this.hasDescription);

        if (this.iconWidget.getIcon() != null)
        {
            this.addWidget(this.iconWidget);
        }
    }

    @Override
    public void updateSubWidgetPositions()
    {
        super.updateSubWidgetPositions();

        this.configButton.setPosition(this.getRight() - 14, this.getY() + 3);

        if (this.currentInfo == null)
        {
            return;
        }

        int x = this.getX() + 4;
        int y = this.getY() + 4;

        this.infoTextLabel.setPosition(x, y);

        if (this.hasDescription)
        {
            this.descriptionLabel.setPosition(x + 4, this.infoTextLabel.getBottom() + 2);
        }

        int offX = (this.getWidth() - this.iconWidget.getWidth()) / 2;
        this.iconWidget.setPosition(this.getX() + offX, this.getBottom() - this.iconWidget.getHeight() - 4);
    }

    protected void openConfigScreen()
    {
        BaseScreen.openPopupScreenWithCurrentScreenAsParent(new SchematicInfoConfigScreen());
    }

    @Nullable
    public SchematicInfo getSelectedSchematicInfo()
    {
        return this.currentInfo;
    }

    public void clearCache()
    {
        this.infoCache.clearCache();
    }

    public void setActiveEntry(@Nullable T entry)
    {
        if (entry != null)
        {
            this.currentInfo = this.infoCache.getOrCacheSchematicInfo(entry);
        }
        else
        {
            this.currentInfo = null;
        }

        this.onActiveEntryChanged();
    }

    protected void onActiveEntryChanged()
    {
        this.updateWidgetState();
        this.updateSubWidgetPositions();
        this.reAddSubWidgets();
    }

    @Override
    public void updateWidgetState()
    {
        // Note: the text needs to be updated first, to know the available space left for the icon
        this.updateInfoLabelText();
        this.updatePreviewIcon();
    }

    protected void updatePreviewIcon()
    {
        if (this.currentInfo == null || this.currentInfo.texture == null ||
            Configs.Internal.SCHEMATIC_INFO_SHOW_THUMBNAIL.getBooleanValue() == false)
        {
            this.iconWidget.setIcon(null);
            return;
        }

        int iconSize = (int) Math.sqrt(this.currentInfo.texture.getTextureData().length);
        int usableHeight = this.getHeight() - this.infoTextLabel.getHeight() - 12;

        if (this.hasDescription)
        {
            usableHeight -= this.descriptionLabel.getHeight() + 6;
        }

        if (usableHeight < iconSize)
        {
            iconSize = usableHeight;
        }

        // No point showing so small previews that you can't see anything from it
        if (iconSize < 10)
        {
            this.iconWidget.setIcon(null);
        }
        else
        {
            this.iconWidget.setIcon(new BaseIcon(0, 0, iconSize, iconSize, iconSize, iconSize, this.currentInfo.iconName));
        }
    }

    protected void updateInfoLabelText()
    {
        if (this.currentInfo == null)
        {
            this.infoTextLabel.setLines(Collections.emptyList());
            this.descriptionLabel.setLines(Collections.emptyList());
            this.descriptionLabel.getHoverInfoFactory().setTextLines("desc", Collections.emptyList());
            return;
        }

        SchematicMetadata meta = this.currentInfo.schematicMetadata;
        List<StyledTextLine> lines = new ArrayList<>();

        if (Configs.Internal.SCHEMATIC_INFO_SHOW_NAME.getBooleanValue() && meta.getSchematicName().length() > 0)
        {
            StyledTextLine.translate(lines, "litematica.label.schematic_info.name");
            StyledTextLine.translate(lines, "litematica.label.schematic_info.name.value", meta.getSchematicName());
        }

        if (Configs.Internal.SCHEMATIC_INFO_SHOW_AUTHOR.getBooleanValue() &&
            meta.getAuthor().length() > 0)
        {
            StyledTextLine.translate(lines, "litematica.label.schematic_info.schematic_author", meta.getAuthor());
        }

        if (Configs.Internal.SCHEMATIC_INFO_SHOW_CREATION_TIME.getBooleanValue() &&
            meta.getTimeCreated() > 0)
        {
            StyledTextLine.translate(lines, "litematica.label.schematic_info.time_created",
                                     getFormattedDateTime(meta.getTimeCreated()));
        }

        if (Configs.Internal.SCHEMATIC_INFO_SHOW_MODIFICATION_TIME.getBooleanValue() &&
            meta.getTimeModified() > 0 && meta.getTimeModified() != meta.getTimeCreated())
        {
            StyledTextLine.translate(lines, "litematica.label.schematic_info.time_modified",
                                     getFormattedDateTime(meta.getTimeModified()));
        }

        if (Configs.Internal.SCHEMATIC_INFO_SHOW_REGION_COUNT.getBooleanValue() &&
            this.currentInfo.schematicType.getSupportsMultipleRegions() &&
            meta.getRegionCount() > 0)
        {
            StyledTextLine.translate(lines, "litematica.label.schematic_info.region_count", meta.getRegionCount());
        }

        if (Configs.Internal.SCHEMATIC_INFO_SHOW_ENTITY_COUNT.getBooleanValue())
        {
            String str = meta.getEntityCount() >= 0 ? getDisplayStringForPossiblyEmptyLongValue(meta.getEntityCount()) : "???";
            StyledTextLine.translate(lines, "litematica.label.schematic_info.entity_count", str);
        }

        if (Configs.Internal.SCHEMATIC_INFO_SHOW_BLOCK_ENTITY_COUNT.getBooleanValue())
        {
            String str = meta.getBlockEntityCount() >= 0 ? getDisplayStringForPossiblyEmptyLongValue(meta.getBlockEntityCount()) : "???";
            StyledTextLine.translate(lines, "litematica.label.schematic_info.block_entity_count", str);
        }

        Vec3i areaSize = meta.getEnclosingSize();
        String areaSizeStr = StringUtils.translate("litematica.label.schematic_info.dimensions_value",
                                                   areaSize.getX(), areaSize.getY(), areaSize.getZ());

        boolean showVolume = Configs.Internal.SCHEMATIC_INFO_SHOW_TOTAL_VOLUME.getBooleanValue() && meta.getTotalVolume() >= 0;
        boolean showTotalBlocks = Configs.Internal.SCHEMATIC_INFO_SHOW_TOTAL_BLOCKS.getBooleanValue() && meta.getTotalBlocks() >= 0;
        boolean showEnclosingSize = Configs.Internal.SCHEMATIC_INFO_SHOW_ENCLOSING_SIZE.getBooleanValue() && meta.getEnclosingSize().equals(Vec3i.ZERO) == false;

        if (this.getHeight() >= 240)
        {
            if (showTotalBlocks)
            {
                StyledTextLine.translate(lines, "litematica.label.schematic_info.total_blocks",
                                         getDisplayStringForPossiblyEmptyLongValue(meta.getTotalBlocks()));
            }

            if (showVolume)
            {
                StyledTextLine.translate(lines, "litematica.label.schematic_info.total_volume",
                                         getDisplayStringForPossiblyEmptyLongValue(meta.getTotalVolume()));
            }

            if (showEnclosingSize)
            {
                StyledTextLine.translate(lines, "litematica.label.schematic_info.enclosing_size");
                StyledTextLine.translate(lines, "litematica.label.schematic_info.enclosing_size.value", areaSizeStr);
            }
        }
        else
        {
            if (showVolume || showTotalBlocks)
            {
                StyledTextLine.translate(lines, "litematica.label.schematic_info.total_blocks_and_volume",
                                         getDisplayStringForPossiblyEmptyLongValue(meta.getTotalBlocks()),
                                         getDisplayStringForPossiblyEmptyLongValue(meta.getTotalVolume()));
            }

            if (showEnclosingSize)
            {
                StyledTextLine.translate(lines, "litematica.label.schematic_info.enclosing_size_and_value", areaSizeStr);
            }
        }

        if (Configs.Internal.SCHEMATIC_INFO_SHOW_SCHEMATIC_TYPE.getBooleanValue())
        {
            String name = this.currentInfo.schematicType.getDisplayName();
            StyledTextLine.translate(lines, "litematica.label.schematic_info.schematic_type", name);
        }

        if (Configs.Internal.SCHEMATIC_INFO_SHOW_SCHEMATIC_VERSION.getBooleanValue() && meta.getSchematicVersion() >= 0)
        {
            StyledTextLine.translate(lines, "litematica.label.schematic_info.schematic_version", meta.getSchematicVersion());
        }

        MinecraftVersion ver = meta.getMinecraftVersion();

        if (Configs.Internal.SCHEMATIC_INFO_SHOW_MC_VERSION.getBooleanValue())
        {
            if (ver.dataVersion > 0)
            {
                StyledTextLine.translate(lines, "litematica.label.schematic_info.mc_version", ver.displayName, ver.dataVersion);
            }
            else
            {
                StyledTextLine.translate(lines, "litematica.label.schematic_info.mc_version", ver.displayName, "???");
            }
        }

        this.hasDescription = org.apache.commons.lang3.StringUtils.isBlank(meta.getDescription()) == false;

        if (this.hasDescription)
        {
            StyledTextLine.translate(lines, "litematica.label.schematic_info.description");

            int usableHeight = this.getHeight() - 12;
            int maxLines = usableHeight / 12;
            int maxDescLines = maxLines - lines.size();

            StyledText nonWrappedText = StyledText.translate("litematica.label.schematic_info.generic_value", meta.getDescription());
            boolean fitAll = false;
            List<StyledTextLine> descriptionLines;

            if (maxDescLines > 0 && Configs.Internal.SCHEMATIC_INFO_SHOW_DESCRIPTION.getBooleanValue())
            {
                StyledText wrappedText = StyledTextUtils.wrapStyledTextToMaxWidth(nonWrappedText, this.getWidth() - 8);
                List<StyledTextLine> wrappedLines = wrappedText.getLines();

                if (wrappedLines.size() <= maxDescLines)
                {
                    descriptionLines = wrappedLines;
                    fitAll = true;
                }
                else
                {
                    descriptionLines = new ArrayList<>();

                    if (maxDescLines >= 2)
                    {
                        descriptionLines.addAll(wrappedLines.subList(0, maxDescLines - 1));
                    }

                    int more = wrappedLines.size() - maxDescLines - 1;
                    StyledTextLine.translate(descriptionLines, "litematica.label.schematic_info.description.more", more);
                }

            }
            // Has a description, but showing it is not enabled, add a line that can be hovered
            else
            {
                descriptionLines = new ArrayList<>();
                StyledTextLine.translate(descriptionLines, "litematica.label.schematic_info.description.hover");
            }

            this.descriptionLabel.setLines(descriptionLines);

            if (fitAll == false)
            {
                this.descriptionLabel.getHoverInfoFactory().setTextLines("desc", nonWrappedText.getLines());
            }
        }

        this.infoTextLabel.setLines(lines);
    }

    public static String getDisplayStringForPossiblyEmptyLongValue(long value)
    {
        if (value == 0)
        {
            return "-";
        }

        return String.format("%,d", value);
    }

    public static SimpleDateFormat createDateFormat()
    {
        SimpleDateFormat fmt;

        try
        {
            fmt = new SimpleDateFormat(Configs.Generic.DATE_FORMAT.getValue());
        }
        catch (Exception ignore)
        {
            fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        }

        return fmt;
    }

    public static String getFormattedDateTime(long timeMillis)
    {
        SimpleDateFormat dateFormat = createDateFormat();
        return dateFormat.format(new Date(timeMillis));
    }
}
