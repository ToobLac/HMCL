/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.instances;

import com.jfoenix.controls.JFXButton;
import com.jfoenix.controls.JFXListView;
import javafx.beans.InvalidationListener;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.ListCell;
import javafx.scene.control.Skin;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import org.jackhuang.hmcl.game.LauncherHelper;
import org.jackhuang.hmcl.game.Log;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.construct.MDListCell;
import org.jackhuang.hmcl.ui.construct.PageAware;
import org.jackhuang.hmcl.ui.construct.TwoLineListItem;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.util.FXThread;

import java.lang.ref.WeakReference;
import java.util.*;

import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

public class GameProcessPage extends ListPageBase<GameProcessPage.GameProcessHolder> implements DecoratorPage, PageAware {

    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(State.fromTitle(i18n("game.process")));

    @FXThread
    private static final Map<String, Integer> idToLaunchedCount = new HashMap<>();

    @FXThread
    private static final List<GameProcessHolder> processHolders = new ArrayList<>();

    @FXThread
    private static void cleanupProcessListeners() {
        processHolders.removeIf(holder -> holder.exited.get());
    }

    public static void addProcessListener(LauncherHelper.HMCLProcessListener processListener) {
        FXUtils.runInFX(() -> processHolders.add(new GameProcessHolder(processListener)));
    }

    public GameProcessPage() {

    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new GameProcessPageSkin(this);
    }

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    @Override
    public void refresh() {
        setLoading(true);
        cleanupProcessListeners();
        getItems().setAll(processHolders);
        setLoading(false);
    }

    @Override
    public void onPageShown() {
        refresh();
    }

    @Override
    public void onPageHidden() {
        getItems().clear();
    }

    public static final class GameProcessHolder {

        @SuppressWarnings({"unused", "FieldCanBeLocal"})
        private final WeakReference<LauncherHelper.HMCLProcessListener> listenerRef;

        @SuppressWarnings("FieldCanBeLocal")
        private final WeakListenerHolder holder = new WeakListenerHolder();

        private final String title;

        private final ObservableList<Log> logs = FXCollections.observableArrayList();
        private final StringProperty lastLogLine = new SimpleStringProperty("");
        private final BooleanProperty exited = new SimpleBooleanProperty();

        private GameProcessHolder(LauncherHelper.HMCLProcessListener processListener) {
            this.listenerRef = new WeakReference<>(processListener);
            {
                String id = processListener.getGameInstance().getId().id();
                int i = idToLaunchedCount.computeIfAbsent(id, k -> 0) + 1;
                idToLaunchedCount.put(id, i);
                this.title = id + " #" + i;
            }
           {
               Bindings.bindContent(logs, processListener.getLogWindow().getLogs());
               if (!logs.isEmpty()) {
                    lastLogLine.set(logs.get(logs.size() - 1).getLog());
                }
                logs.addListener((InvalidationListener) o -> {
                    if (!logs.isEmpty()) {
                        lastLogLine.set(logs.get(logs.size() - 1).getLog());
                    } else {
                        lastLogLine.set("");
                    }
                });
            }
            holder.onWeakChangeAndOperate(processListener.exitedProperty(), b -> {
                if (b) {
                    var currentLogs = processListener.getLogWindow().getLogs();
                    this.lastLogLine.set(currentLogs.get(currentLogs.size() - 1).getLog()); // I don't know why but this is necessary
                    this.exited.set(true);
                }
            });
        }
    }

    private static final class GameProcessPageSkin extends ToolbarListPageSkin<GameProcessHolder, GameProcessPage> {

        public GameProcessPageSkin(GameProcessPage skinnable) {
            super(skinnable);
        }

        @Override
        protected List<Node> initializeToolbar(GameProcessPage skinnable) {
            return List.of(
                    ToolbarListPageSkin.createToolbarButton2(i18n("button.refresh"), SVG.REFRESH, skinnable::refresh)
            );
        }

        @Override
        protected ListCell<GameProcessHolder> createListCell(JFXListView<GameProcessHolder> listView) {
            return new GameProcessCell(listView);
        }
    }

    private static final class GameProcessCell extends MDListCell<GameProcessHolder> {

        private final TwoLineListItem content = new TwoLineListItem();
        private final JFXButton logWindowButton = FXUtils.newToggleButton4(SVG.TERMINAL);
        private final JFXButton terminateButton = FXUtils.newToggleButton4(SVG.SHUTDOWN);

        public GameProcessCell(JFXListView<GameProcessHolder> listView) {
            super(listView);

            HBox container = new HBox(8);
            container.setPickOnBounds(false);
            container.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(content, Priority.ALWAYS);
            content.setMouseTransparent(true);

            FXUtils.installFastTooltip(logWindowButton, i18n("game.process.show_log"));
            FXUtils.installFastTooltip(terminateButton, i18n("game.process.terminate"));

            container.getChildren().setAll(content, logWindowButton, terminateButton);
            StackPane.setMargin(container, new Insets(8));
            getContainer().getChildren().setAll(container);
        }

        @Override
        protected void updateControl(GameProcessHolder item, boolean empty) {
            if (item == null || empty) {
                logWindowButton.setOnAction(null);
                terminateButton.setOnAction(null);
                terminateButton.disableProperty().unbind();
                return;
            }

            content.setTitle(item.title);
            content.subtitleProperty().bind(item.lastLogLine);

            logWindowButton.setOnAction(event -> {
                var listener = item.listenerRef.get();
                if (listener != null) {
                    listener.getLogWindow().show();
                } else {
                    LogWindow logWindow = new LogWindow();
                    logWindow.logLines(item.logs);
                    logWindow.show();
                }
            });
            terminateButton.setOnAction(event -> {
                var listener = item.listenerRef.get();
                if (listener != null) listener.getProcess().stop();
            });
            terminateButton.disableProperty().bind(item.exited);
        }
    }

}
