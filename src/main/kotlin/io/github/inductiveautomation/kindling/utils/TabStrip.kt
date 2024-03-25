package io.github.inductiveautomation.kindling.utils

import com.formdev.flatlaf.extras.FlatSVGIcon
import com.formdev.flatlaf.extras.components.FlatTextArea
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Container
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JPanel
import javax.swing.JPopupMenu

interface PopupMenuCustomizer {
    fun customizePopupMenu(menu: JPopupMenu)
}

interface FloatableComponent {
    val icon: Icon?
    val tabName: String
    val tabTooltip: String?
}

@Suppress("LeakingThis")
open class TabStrip : DnDTabbedPane() {
    init {
        tabPlacement = TOP
        tabLayoutPolicy = SCROLL_TAB_LAYOUT
        tabAlignment = TabAlignment.leading
        isTabsClosable = true

        setTabCloseCallback { _, i ->
            removeTabAt(i)
        }

        attachPopupMenu { event ->
            val tabIndex = indexAtLocation(event.x, event.y)
            if (tabIndex == -1) return@attachPopupMenu null

            val tab = getComponentAt(tabIndex) as JComponent

            JPopupMenu().apply {
                if (isTabsClosable) {
                    add(
                        Action("Close") {
                            removeClosableTabAt(tabIndex)
                        },
                    )
                    add(
                        Action("Close Other Tabs") {
                            for (i in tabCount - 1 downTo 0) {
                                if (i != tabIndex) {
                                    removeClosableTabAt(i)
                                }
                            }
                        },
                    )
                    add(
                        Action("Close Tabs Left") {
                            for (i in tabIndex - 1 downTo 0) {
                                removeClosableTabAt(i)
                            }
                        },
                    )
                    add(
                        Action("Close Tabs Right") {
                            for (i in tabCount - 1 downTo tabIndex + 1) {
                                removeClosableTabAt(i)
                            }
                        },
                    )
                    val closable = isTabClosable(tabIndex)
                    add(
                        Action(if (closable) "Pin" else "Unpin") {
                            setTabClosable(tabIndex, !closable)
                        },
                    )
                }
                if (tab is FloatableComponent) {
                    add(
                        Action("Float") {
                            val frame = createPopupFrame(tab)
                            frame.isVisible = true
                        },
                    )
                }
                if (tab is PopupMenuCustomizer) {
                    tab.customizePopupMenu(this)
                }
            }
        }
    }

    private fun removeClosableTabAt(index: Int) {
        if (isTabClosable(index)) {
            removeTabAt(index)
        }
    }

    val indices: IntRange
        get() = 0 until tabCount

    fun <T> addTab(
        component: T,
        tabName: String = component.tabName,
        tabTooltip: String? = component.tabTooltip,
        icon: Icon? = component.icon,
        select: Boolean = true,
    ) where T : Container, T : FloatableComponent {
        addTab(tabName.truncate(30), icon, component, tabTooltip)
        if (select) {
            selectedIndex = indices.last
        }
    }

    fun <T> addLazyTab(
        tabName: String,
        tabTooltip: String? = null,
        icon: Icon? = null,
        component: () -> T,
    ) where T : Container, T : FloatableComponent {
        addTab(
            tabName.truncate(30),
            icon,
            LazyTab(tabName, icon, tabTooltip, component),
            tabTooltip,
        )
    }

    fun <E : Throwable> addErrorTab(
        error: E,
        description: (E) -> String = { it.message ?: "Error" },
    ) {
        addTab(
            "ERROR",
            FlatSVGIcon("icons/bx-error.svg"),
            FlatScrollPane(
                FlatTextArea().apply {
                    isEditable = false
                    text = buildString {
                        append(description(error))
                        append((error.cause ?: error).stackTraceToString())
                    }
                },
            ),
        )
    }

    open class LazyTab(
        override val tabName: String,
        override val icon: Icon?,
        override val tabTooltip: String?,
        supplier: () -> Component,
    ) : JPanel(BorderLayout()), PopupMenuCustomizer, FloatableComponent {
        val component = lazy(supplier)

        init {
            addComponentListener(
                object : ComponentAdapter() {
                    override fun componentShown(e: ComponentEvent) {
                        if (!component.isInitialized()) {
                            val actualComponent = component.value
                            add(actualComponent, BorderLayout.CENTER)
                        }
                    }
                },
            )
        }

        final override fun addComponentListener(l: ComponentListener) = super.addComponentListener(l)

        override fun customizePopupMenu(menu: JPopupMenu) {
            (component.value as? PopupMenuCustomizer)?.customizePopupMenu(menu)
        }
    }

    private fun <T> createPopupFrame(tab: T): JFrame where T : Container, T : FloatableComponent {
        return jFrame(tab.tabName, 1024, 768) {
            contentPane = tab

            jMenuBar = JMenuBar().apply {
                add(
                    JMenu("Actions").apply {
                        add(
                            Action(name = "Unfloat") {
                                addTab(
                                    tab.tabName,
                                    tab.icon,
                                    tab,
                                    tab.tabTooltip,
                                )
                                dispose()
                            },
                        )
                    },
                )
            }
        }
    }
}
