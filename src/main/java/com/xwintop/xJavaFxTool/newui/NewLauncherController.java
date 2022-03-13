package com.xwintop.xJavaFxTool.newui;

import com.xwintop.xJavaFxTool.XJavaFxToolApplication;
import com.xwintop.xJavaFxTool.controller.IndexController;
import com.xwintop.xJavaFxTool.controller.index.PluginManageController;
import com.xwintop.xJavaFxTool.event.AppEvents;
import com.xwintop.xJavaFxTool.event.PluginEvent;
import com.xwintop.xJavaFxTool.model.PluginJarInfo;
import com.xwintop.xJavaFxTool.plugin.PluginManager;
import com.xwintop.xJavaFxTool.plugin.PluginParser;
import com.xwintop.xJavaFxTool.services.index.SystemSettingService;
import com.xwintop.xcore.javafx.FxApp;
import com.xwintop.xcore.javafx.dialog.FxDialog;
import javafx.beans.Observable;
import javafx.scene.control.CheckMenuItem;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebView;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;

import java.awt.*;
import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class NewLauncherController {

    public static final String FAVORITE_CATEGORY_NAME = "置顶";

    public VBox pluginCategories;

    public WebView startWebView;

    public TabPane tabPane;

    public TextField txtSearch;

    private ContextMenu itemContextMenu;

    // 实现搜索用
    private final List<PluginItemController> pluginItemControllers = new ArrayList<>();

    private final Map<String, PluginCategoryController> categoryControllers = new HashMap<>();

    public void initialize() {
        NewLauncherService.getInstance().setTabPane(tabPane);
        txtSearch.textProperty().addListener(this::onSearchKeywordChanged);

        initContextMenu();
        loadPlugins();  // 加载插件列表到界面上

        startWebView.getEngine().load(IndexController.QQ_URL); // 额外再打开一个反馈页面，可关闭

        AppEvents.addEventHandler(PluginEvent.PLUGIN_DOWNLOADED, pluginEvent -> {
            loadPlugins();
        });
    }

    private void initContextMenu() {
        CheckMenuItem chkFavorite = new CheckMenuItem("置顶");
        chkFavorite.setStyle("-fx-padding: 0 35 0 0");

        this.itemContextMenu = new ContextMenu(chkFavorite);

        chkFavorite.setOnAction(event -> {
            CheckMenuItem _this = (CheckMenuItem) event.getSource();
            PluginItemController pluginItemController = NewLauncherService.getInstance().getCurrentPluginItem();
            setFavorite(pluginItemController, _this.isSelected());
        });
    }

    public void onSearchKeywordChanged(Observable ob, String _old, String keyword) {
        boolean notSearching = StringUtils.isBlank(keyword);
        pluginItemControllers.forEach(itemController -> {
            itemController.setVisible(notSearching || itemController.matchKeyword(keyword));
        });
    }

    private void setFavorite(PluginItemController itemController, boolean isFavorite) {
        if (itemController == null) {
            return;
        }

        itemController.getPluginJarInfo().setIsFavorite(isFavorite);
        PluginManager.getInstance().saveToFileQuietly();
        loadPlugins();
    }

    /**
     * 加载/刷新插件列表
     */
    private void loadPlugins() {

        this.pluginCategories.getChildren().clear();
        this.pluginItemControllers.clear();
        this.categoryControllers.clear();

        PluginManager pluginManager = PluginManager.getInstance();
        pluginManager.loadLocalPlugins();
        pluginManager.getEnabledPluginList().forEach(this::loadPlugin);
    }

    /**
     * 加载单个插件到界面，要求插件已经经过 {@link PluginParser#parse(File, PluginJarInfo)} 解析
     *
     * @param jarInfo 插件信息
     */
    private void loadPlugin(PluginJarInfo jarInfo) {

        if (!jarInfo.getFile().exists()) {
            log.info("跳过插件 {}: 文件不存在", jarInfo.getName());
            return;
        }

        if (BooleanUtils.isFalse(jarInfo.getIsEnable())) {
            log.info("跳过插件 {}: 插件未启用", jarInfo.getName());
            return;
        }

        String menuParentTitle = jarInfo.getMenuParentTitle();
        if (menuParentTitle == null) {
            log.info("跳过插件 {}: menuParentTitle 为空", jarInfo.getName());
            return;
        }

        String categoryName = jarInfo.getIsFavorite() ? FAVORITE_CATEGORY_NAME : XJavaFxToolApplication.RESOURCE_BUNDLE.getString(menuParentTitle);

        PluginCategoryController category = categoryControllers.computeIfAbsent(
            categoryName, __ -> {
                PluginCategoryController _category =
                    PluginCategoryController.newInstance(categoryName);
                addCategory(_category);
                return _category;
            }
        );

        PluginItemController item = PluginItemController.newInstance(jarInfo);
        item.setContextMenu(itemContextMenu);
        category.addItem(item);

        if (!pluginItemControllers.contains(item)) {
            pluginItemControllers.add(item);
        }
    }

    private void addCategory(PluginCategoryController category) {
        if (category.lblCategoryName.getText().equals(FAVORITE_CATEGORY_NAME)) {
            this.pluginCategories.getChildren().add(0, category.root);
        } else {
            this.pluginCategories.getChildren().add(category.root);
        }
    }

    public TabPane getTabPane() {
        return tabPane;
    }

    public void openConfigDialog() {
        SystemSettingService.openSystemSettings("设置");
    }

    public void openPluginManager() {
        new FxDialog<PluginManageController>()
            .setBodyFxml(PluginManageController.FXML)
            .setOwner(FxApp.primaryStage)
            .setResizable(true)
            .setTitle(XJavaFxToolApplication.RESOURCE_BUNDLE.getString("plugin_manage"))
            .setPrefWidth(800)
            .withStage(stage -> stage.setOnCloseRequest(event -> loadPlugins()))
            .show();
    }

    public void openProjectUrl() {
        try {
            Desktop.getDesktop().browse(new URI("https://gitee.com/xwintop/xJavaFxTool"));
        } catch (Exception e) {
            log.error("打开项目地址失败", e);
        }
    }
}
