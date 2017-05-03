/*
 * Copyright 2016 - 2017 Michael Rapp
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package de.mrapp.android.tabswitcher.layout.phone;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.Toolbar;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;

import java.util.Collections;

import de.mrapp.android.tabswitcher.Animation;
import de.mrapp.android.tabswitcher.Layout;
import de.mrapp.android.tabswitcher.PeekAnimation;
import de.mrapp.android.tabswitcher.R;
import de.mrapp.android.tabswitcher.RevealAnimation;
import de.mrapp.android.tabswitcher.SwipeAnimation;
import de.mrapp.android.tabswitcher.SwipeAnimation.SwipeDirection;
import de.mrapp.android.tabswitcher.Tab;
import de.mrapp.android.tabswitcher.TabSwitcher;
import de.mrapp.android.tabswitcher.TabSwitcherDecorator;
import de.mrapp.android.tabswitcher.iterator.AbstractTabItemIterator;
import de.mrapp.android.tabswitcher.iterator.ArrayTabItemIterator;
import de.mrapp.android.tabswitcher.iterator.TabItemIterator;
import de.mrapp.android.tabswitcher.layout.AbstractDragHandler;
import de.mrapp.android.tabswitcher.layout.AbstractDragHandler.DragState;
import de.mrapp.android.tabswitcher.layout.AbstractTabSwitcherLayout;
import de.mrapp.android.tabswitcher.layout.Arithmetics;
import de.mrapp.android.tabswitcher.layout.Arithmetics.Axis;
import de.mrapp.android.tabswitcher.model.State;
import de.mrapp.android.tabswitcher.model.TabItem;
import de.mrapp.android.tabswitcher.model.TabSwitcherModel;
import de.mrapp.android.tabswitcher.model.Tag;
import de.mrapp.android.tabswitcher.util.ThemeHelper;
import de.mrapp.android.util.view.AttachedViewRecycler;
import de.mrapp.android.util.view.ViewRecycler;

import static de.mrapp.android.util.Condition.ensureGreater;
import static de.mrapp.android.util.Condition.ensureTrue;

/**
 * A layout, which implements the functionality of a {@link TabSwitcher} on smartphones.
 *
 * @author Michael Rapp
 * @since 0.1.0
 */
public class PhoneTabSwitcherLayout extends AbstractTabSwitcherLayout<Integer>
        implements PhoneDragHandler.Callback {

    /**
     * A layout listener, which encapsulates another listener, which is notified, when the listener
     * has been invoked a specific number of times.
     */
    private class CompoundLayoutListener implements OnGlobalLayoutListener {

        /**
         * The number of times, the listener must still be invoked, until the encapsulated listener
         * is notified.
         */
        private int count;

        /**
         * The encapsulated listener;
         */
        private final OnGlobalLayoutListener listener;

        /**
         * Creates a new layout listener, which encapsulates another listener, which is notified,
         * when the listener has been invoked a specific number of times.
         *
         * @param count
         *         The number of times, the listener should be invoked until the encapsulated
         *         listener is notified, as an {@link Integer} value. The count must be greater than
         *         0
         * @param listener
         *         The encapsulated listener, which should be notified, when the listener has been
         *         notified the given number of times, as an instance of the type {@link
         *         OnGlobalLayoutListener} or null, if no listener should be notified
         */
        CompoundLayoutListener(final int count, @Nullable final OnGlobalLayoutListener listener) {
            ensureGreater(count, 0, "The count must be greater than 0");
            this.count = count;
            this.listener = listener;
        }

        @Override
        public void onGlobalLayout() {
            if (--count == 0) {
                if (listener != null) {
                    listener.onGlobalLayout();
                }
            }
        }

    }

    /**
     * The ratio, which specifies the maximum space between the currently selected tab and its
     * predecessor in relation to the default space.
     */
    private static final float SELECTED_TAB_SPACING_RATIO = 1.5f;

    /**
     * The ratio, which specifies the minimum space between two neighboring tabs in relation to the
     * maximum space.
     */
    private static final float MIN_TAB_SPACING_RATIO = 0.375f;

    /**
     * The number of tabs, which are contained by a stack.
     */
    private final int stackedTabCount;

    /**
     * The inset of tabs in pixels.
     */
    private final int tabInset;

    /**
     * The width of the border, which is drawn around the preview of tabs.
     */
    private final int tabBorderWidth;

    /**
     * The height of a tab's title container in pixels.
     */
    private final int tabTitleContainerHeight;

    /**
     * The maximum camera distance, when tilting a tab, in pixels.
     */
    private final int maxCameraDistance;

    /**
     * The alpha of a tab, when it is swiped.
     */
    private final float swipedTabAlpha;

    /**
     * The scale of a tab, when it is swiped.
     */
    private final float swipedTabScale;

    /**
     * The duration of the animation, which is used to show the switcher.
     */
    private final long showSwitcherAnimationDuration;

    /**
     * The duration of the animation, which is used to hide the switcher.
     */
    private final long hideSwitcherAnimationDuration;

    /**
     * The duration of the animation, which is used to show or hide the toolbar.
     */
    private final long toolbarVisibilityAnimationDuration;

    /**
     * The delay of the animation, which is used to show or hide the toolbar.
     */
    private final long toolbarVisibilityAnimationDelay;

    /**
     * The duration of the animation, which is used to swipe tabs.
     */
    private final long swipeAnimationDuration;

    /**
     * The delay of the animation, which is used to remove all tabs.
     */
    private final long clearAnimationDelay;

    /**
     * The duration of the animation, which is used to relocate tabs.
     */
    private final long relocateAnimationDuration;

    /**
     * The delay of the animation, which is used to relocate tabs.
     */
    private final long relocateAnimationDelay;

    /**
     * The duration of the animation, which is used to revert overshoots.
     */
    private final long revertOvershootAnimationDuration;

    /**
     * The duration of a reveal animation.
     */
    private final long revealAnimationDuration;

    /**
     * The duration of a peek animation.
     */
    private final long peekAnimationDuration;

    /**
     * The maximum angle, tabs can be rotated by, when overshooting at the start, in degrees.
     */
    private final float maxStartOvershootAngle;

    /**
     * The maximum angle, tabs can be rotated by, when overshooting at the end, in degrees.
     */
    private final float maxEndOvershootAngle;

    /**
     * The drag handler, which is used by the layout.
     */
    private PhoneDragHandler dragHandler;

    /**
     * The view recycler, which allows to recycle the views, which are associated with tabs.
     */
    private ViewRecycler<Tab, Void> contentViewRecycler;

    /**
     * The adapter, which allows to inflate the views, which are used to visualize tabs.
     */
    private PhoneRecyclerAdapter recyclerAdapter;

    /**
     * The view recycler, which allows to recycle the views, which are used to visualize tabs.
     */
    private AttachedViewRecycler<TabItem, Integer> tabViewRecycler;

    /**
     * The view group, which contains the tab switcher's tabs.
     */
    private ViewGroup tabContainer;

    /**
     * The toolbar, which is shown, when the tab switcher is shown.
     */
    private Toolbar toolbar;

    /**
     * The bottom margin of a view, which visualizes a tab.
     */
    private int tabViewBottomMargin;

    /**
     * The animation, which is used to show or hide the toolbar.
     */
    private ViewPropertyAnimator toolbarAnimation;

    /**
     * Adapts the decorator.
     */
    private void adaptDecorator() {
        recyclerAdapter.clearCachedPreviews();
    }

    /**
     * Adapts the margin of the toolbar, which is shown, when the tab switcher is shown.
     */
    private void adaptToolbarMargin() {
        FrameLayout.LayoutParams layoutParams =
                (FrameLayout.LayoutParams) toolbar.getLayoutParams();
        layoutParams.setMargins(getModel().getPaddingLeft(), getModel().getPaddingTop(),
                getModel().getPaddingRight(), 0);
    }

    /**
     * Calculates the position of a tab in relation to the position of its predecessor.
     *
     * @param predecessorPosition
     *         The position of the predecessor in pixels as a {@link Float} value
     * @param maxTabSpacing
     *         The maximum space between two neighboring tabs in pixels as a {@link Float} value
     * @return The position, which has been calculated, as a {@link Float} value
     */
    private float calculateSuccessorPosition(final float predecessorPosition,
                                             final float maxTabSpacing) {
        float ratio = Math.min(1,
                predecessorPosition / calculateAttachedPosition(getTabSwitcher().getCount()));
        float minTabSpacing = calculateMinTabSpacing();
        return predecessorPosition - minTabSpacing - (ratio * (maxTabSpacing - minTabSpacing));
    }

    /**
     * Calculates and returns the position of a tab, when it is swiped.
     *
     * @return The position, which has been calculated, in pixels as an {@link Float} value
     */
    private float calculateSwipePosition() {
        return getArithmetics().getSize(Axis.ORTHOGONAL_AXIS, getTabSwitcher());
    }

    /**
     * Calculates and returns the maximum space between a specific tab and its predecessor. The
     * maximum space is greater for the currently selected tab.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, the maximum space should be returned for,
     *         as an instance of the class {@link TabItem} or null, if the default maximum space
     *         should be returned
     * @return The maximum space between the given tab and its predecessor in pixels as a {@link
     * Float} value
     */
    private float calculateMaxTabSpacing(@Nullable final TabItem tabItem) {
        float totalSpace = getArithmetics().getSize(Axis.DRAGGING_AXIS, tabContainer) -
                (getTabSwitcher().getLayout() == Layout.PHONE_PORTRAIT &&
                        getModel().areToolbarsShown() ? toolbar.getHeight() + tabInset : 0);
        float maxTabSpacing;
        int count = getModel().getCount();

        if (count <= 2) {
            maxTabSpacing = totalSpace * 0.66f;
        } else if (count == 3) {
            maxTabSpacing = totalSpace * 0.33f;
        } else if (count == 4) {
            maxTabSpacing = totalSpace * 0.3f;
        } else {
            maxTabSpacing = totalSpace * 0.25f;
        }

        return count > 4 && tabItem != null &&
                tabItem.getTab() == getTabSwitcher().getSelectedTab() ?
                maxTabSpacing * SELECTED_TAB_SPACING_RATIO : maxTabSpacing;
    }

    /**
     * Calculates and returns the minimum space between two neighboring tabs.
     *
     * @return The minimum space between two neighboring tabs in pixels as a {@link Float} value
     */
    private float calculateMinTabSpacing() {
        return calculateMaxTabSpacing(null) * MIN_TAB_SPACING_RATIO;
    }

    /**
     * Calculates and returns the bottom margin of a view, which visualizes a tab.
     *
     * @param view
     *         The view, whose bottom margin should be calculated, as an instance of the class
     *         {@link View}. The view may not be null
     * @return The bottom margin, which has been calculated, in pixels as an {@link Integer} value
     */
    private int calculateBottomMargin(@NonNull final View view) {
        float tabHeight = (view.getHeight() - 2 * tabInset) * getArithmetics().getScale(view, true);
        float containerHeight = getArithmetics().getSize(Axis.Y_AXIS, tabContainer);
        int toolbarHeight = getModel().areToolbarsShown() ? toolbar.getHeight() - tabInset : 0;
        int stackHeight = getTabSwitcher().getLayout() == Layout.PHONE_LANDSCAPE ? 0 :
                getStackedTabCount() * getStackedTabSpacing();
        return Math.round(tabHeight + tabInset + toolbarHeight + stackHeight -
                (containerHeight - getModel().getPaddingTop() - getModel().getPaddingBottom()));
    }

    /**
     * Animates the bottom margin of a specific view.
     *
     * @param view
     *         The view, whose bottom margin should be animated, as an instance of the class {@link
     *         View}. The view may not be null
     * @param margin
     *         The bottom margin, which should be set by the animation, as an {@link Integer} value
     * @param duration
     *         The duration of the animation in milliseconds as a {@link Long} value
     * @param delay
     *         The delay of the animation in milliseconds as a {@link Long} value
     */
    private void animateBottomMargin(@NonNull final View view, final int margin,
                                     final long duration, final long delay) {
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) view.getLayoutParams();
        final int initialMargin = layoutParams.bottomMargin;
        ValueAnimator animation = ValueAnimator.ofInt(margin - initialMargin);
        animation.setDuration(duration);
        animation.addListener(new AnimationListenerWrapper(null));
        animation.setInterpolator(new AccelerateDecelerateInterpolator());
        animation.setStartDelay(delay);
        animation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                FrameLayout.LayoutParams layoutParams =
                        (FrameLayout.LayoutParams) view.getLayoutParams();
                layoutParams.bottomMargin = initialMargin + (int) animation.getAnimatedValue();
                view.setLayoutParams(layoutParams);
            }

        });

        animation.start();
    }

    /**
     * Animates the visibility of the toolbar, which is shown, when the tab switcher is shown.
     *
     * @param visible
     *         True, if the toolbar should become visible, false otherwise
     * @param delay
     *         The delay of the animation in milliseconds as a {@link Long} value
     */
    private void animateToolbarVisibility(final boolean visible, final long delay) {
        if (toolbarAnimation != null) {
            toolbarAnimation.cancel();
        }

        float targetAlpha = visible ? 1 : 0;

        if (toolbar.getAlpha() != targetAlpha) {
            toolbarAnimation = toolbar.animate();
            toolbarAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
            toolbarAnimation.setDuration(toolbarVisibilityAnimationDuration);
            toolbarAnimation.setStartDelay(delay);
            toolbarAnimation.alpha(targetAlpha);
            toolbarAnimation.start();
        }
    }

    /**
     * Shows the tab switcher in an animated manner.
     */
    private void animateShowSwitcher() {
        TabItem[] tabItems = calculateInitialTabItems(-1, -1);
        AbstractTabItemIterator iterator = new InitialTabItemIterator(tabItems, false, 0);
        TabItem tabItem;

        while ((tabItem = iterator.next()) != null) {
            if (tabItem.getTab() == getModel().getSelectedTab() || tabItem.isVisible()) {
                tabViewRecycler.inflate(tabItem);
                View view = tabItem.getView();

                if (!ViewCompat.isLaidOut(view)) {
                    view.getViewTreeObserver().addOnGlobalLayoutListener(
                            new LayoutListenerWrapper(view,
                                    createShowSwitcherLayoutListener(tabItem)));
                } else {
                    animateShowSwitcher(tabItem, createUpdateViewAnimationListener(tabItem));
                }
            }
        }

        animateToolbarVisibility(getModel().areToolbarsShown(), toolbarVisibilityAnimationDelay);
    }

    /**
     * Calculates and returns the tab items, which correspond to the tabs, when the tab switcher is
     * shown initially.
     *
     * @param firstVisibleTabIndex
     *         The index of the first visible tab as an {@link Integer} value or -1, if the index is
     *         unknown
     * @param firstVisibleTabPosition
     *         The position of the first visible tab in pixels as a {@link Float} value or -1, if
     *         the position is unknown
     * @return An array, which contains the tab items, as an array of the type {@link TabItem}. The
     * array may not be null
     */
    @NonNull
    private TabItem[] calculateInitialTabItems(final int firstVisibleTabIndex,
                                               final float firstVisibleTabPosition) {
        dragHandler.reset(getDragThreshold());
        setFirstVisibleIndex(-1);
        TabItem[] tabItems = new TabItem[getModel().getCount()];

        if (!getModel().isEmpty()) {
            int selectedTabIndex = getModel().getSelectedTabIndex();
            float attachedPosition = calculateAttachedPosition(getModel().getCount());
            int referenceIndex = firstVisibleTabIndex != -1 && firstVisibleTabPosition != -1 ?
                    firstVisibleTabIndex : selectedTabIndex;
            float referencePosition = firstVisibleTabIndex != -1 && firstVisibleTabPosition != -1 ?
                    firstVisibleTabPosition : attachedPosition;
            referencePosition =
                    Math.min(calculateMaxEndPosition(referenceIndex), referencePosition);
            AbstractTabItemIterator iterator =
                    new InitialTabItemIterator(tabItems, false, referenceIndex);
            TabItem tabItem;

            while ((tabItem = iterator.next()) != null) {
                TabItem predecessor = iterator.previous();
                float position;

                if (tabItem.getIndex() == getModel().getCount() - 1) {
                    position = 0;
                } else if (tabItem.getIndex() == referenceIndex) {
                    position = referencePosition;
                } else {
                    position = calculateSuccessorPosition(tabItem, predecessor);
                }

                Pair<Float, State> pair =
                        clipTabPosition(tabItem.getIndex(), position, predecessor);
                tabItem.getTag().setPosition(pair.first);
                tabItem.getTag().setState(pair.second);

                if (pair.second != State.FLOATING) {
                    break;
                }
            }

            boolean overshooting =
                    referenceIndex == getModel().getCount() - 1 || isOvershootingAtEnd();
            iterator = new InitialTabItemIterator(tabItems, false, 0);
            float minTabSpacing = calculateMinTabSpacing();
            float defaultTabSpacing = calculateMaxTabSpacing(null);
            TabItem selectedTabItem =
                    TabItem.create(getTabSwitcher(), tabViewRecycler, selectedTabIndex);
            float maxTabSpacing = calculateMaxTabSpacing(selectedTabItem);
            TabItem currentReferenceTabItem = iterator.getItem(referenceIndex);

            while ((tabItem = iterator.next()) != null &&
                    (overshooting || tabItem.getIndex() < referenceIndex)) {
                float currentTabSpacing = calculateMaxTabSpacing(currentReferenceTabItem);
                TabItem predecessor = iterator.peek();
                Pair<Float, State> pair;

                if (overshooting) {
                    float position;

                    if (referenceIndex > tabItem.getIndex()) {
                        position = maxTabSpacing +
                                ((getModel().getCount() - 1 - tabItem.getIndex() - 1) *
                                        defaultTabSpacing);
                    } else {
                        position = (getModel().getCount() - 1 - tabItem.getIndex()) *
                                defaultTabSpacing;
                    }

                    pair = clipTabPosition(tabItem.getIndex(), position, predecessor);
                } else if (referencePosition >= attachedPosition - currentTabSpacing) {
                    float position;

                    if (selectedTabIndex > tabItem.getIndex() &&
                            selectedTabIndex <= referenceIndex) {
                        position = referencePosition + maxTabSpacing +
                                ((referenceIndex - tabItem.getIndex() - 1) * defaultTabSpacing);
                    } else {
                        position = referencePosition +
                                ((referenceIndex - tabItem.getIndex()) * defaultTabSpacing);
                    }

                    pair = clipTabPosition(tabItem.getIndex(), position, predecessor);
                } else {
                    TabItem successor = iterator.previous();
                    float successorPosition = successor.getTag().getPosition();
                    float position = (attachedPosition * (successorPosition + minTabSpacing)) /
                            (minTabSpacing + attachedPosition - currentTabSpacing);
                    pair = clipTabPosition(tabItem.getIndex(), position, predecessor);

                    if (pair.first >= attachedPosition - currentTabSpacing) {
                        currentReferenceTabItem = tabItem;
                        referencePosition = pair.first;
                        referenceIndex = tabItem.getIndex();
                    }
                }

                tabItem.getTag().setPosition(pair.first);
                tabItem.getTag().setState(pair.second);

                if (getFirstVisibleIndex() == -1 && pair.second == State.FLOATING) {
                    setFirstVisibleIndex(tabItem.getIndex());
                }
            }

            if (getFirstVisibleIndex() == -1) {
                setFirstVisibleIndex(referenceIndex);
            }
        }

        dragHandler.setCallback(this);
        return tabItems;
    }

    /**
     * Adds all tabs, which are contained by an array, to the tab switcher.
     *
     * @param index
     *         The index, the first tab should be added at, as an {@link Integer} value
     * @param tabs
     *         The array, which contains the tabs, which should be added, as an array of the type
     *         {@link Tab}. The array may not be null
     * @param animation
     *         The animation, which should be used to add the tabs, as an instance of the class
     *         {@link Animation}. The animation may not be null
     */
    private void addAllTabs(final int index, @NonNull final Tab[] tabs,
                            @NonNull final Animation animation) {
        if (tabs.length > 0) {
            if (getModel().isSwitcherShown()) {
                SwipeAnimation swipeAnimation =
                        animation instanceof SwipeAnimation ? (SwipeAnimation) animation :
                                new SwipeAnimation.Builder().create();
                TabItem[] tabItems = new TabItem[tabs.length];
                OnGlobalLayoutListener compoundListener = new CompoundLayoutListener(tabs.length,
                        createSwipeLayoutListener(tabItems, swipeAnimation));

                for (int i = 0; i < tabs.length; i++) {
                    Tab tab = tabs[i];
                    TabItem tabItem = new TabItem(index + i, tab);
                    tabItems[i] = tabItem;
                    inflateView(tabItem, compoundListener);
                }
            } else if (!getModel().isSwitcherShown()) {
                toolbar.setAlpha(0);

                if (getModel().getSelectedTab() == tabs[0]) {
                    TabItem tabItem = TabItem.create(getTabSwitcher(), tabViewRecycler, index);
                    inflateView(tabItem, createAddSelectedTabLayoutListener(tabItem));
                }
            }
        }
    }

    /**
     * Animates the position and size of a specific tab item in order to show the tab switcher.
     *
     * @param tabItem
     *         The tab item, which should be animated, as an instance of the class {@link TabItem}.
     *         The tab item may not be null
     * @param listener
     *         The listener, which should be notified about the animation's progress, as an instance
     *         of the type {@link AnimatorListener} or null, if no listener should be notified
     */
    private void animateShowSwitcher(@NonNull final TabItem tabItem,
                                     @Nullable final AnimatorListener listener) {
        animateShowSwitcher(tabItem, showSwitcherAnimationDuration,
                new AccelerateDecelerateInterpolator(), listener);
    }

    /**
     * Animates the position and size of a specific tab in order to show the tab switcher.
     *
     * @param tabItem
     *         The tab item, which should be animated, as an instance of the class {@link TabItem}.
     *         The tab item may not be null
     * @param duration
     *         The duration of the animation in milliseconds as a {@link Long} value
     * @param interpolator
     *         The interpolator, which should be used by the animation, as an instance of the type
     *         {@link Interpolator}. The interpolator may not be null
     * @param listener
     *         The listener, which should be notified about the animation's progress, as an instance
     *         of the type {@link AnimatorListener} or null, if no listener should be notified
     */
    private void animateShowSwitcher(@NonNull final TabItem tabItem, final long duration,
                                     @NonNull final Interpolator interpolator,
                                     @Nullable final AnimatorListener listener) {
        View view = tabItem.getView();
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) view.getLayoutParams();
        view.setX(layoutParams.leftMargin);
        view.setY(layoutParams.topMargin);
        getArithmetics().setScale(Axis.DRAGGING_AXIS, view, 1);
        getArithmetics().setScale(Axis.ORTHOGONAL_AXIS, view, 1);
        getArithmetics().setPivot(Axis.DRAGGING_AXIS, view,
                getArithmetics().getPivot(Axis.DRAGGING_AXIS, view, DragState.NONE));
        getArithmetics().setPivot(Axis.ORTHOGONAL_AXIS, view,
                getArithmetics().getPivot(Axis.ORTHOGONAL_AXIS, view, DragState.NONE));
        float scale = getArithmetics().getScale(view, true);
        int selectedTabIndex = getModel().getSelectedTabIndex();

        if (tabItem.getIndex() < selectedTabIndex) {
            getArithmetics().setPosition(Axis.DRAGGING_AXIS, view,
                    getArithmetics().getSize(Axis.DRAGGING_AXIS, tabContainer));
        } else if (tabItem.getIndex() > selectedTabIndex) {
            getArithmetics().setPosition(Axis.DRAGGING_AXIS, view,
                    getTabSwitcher().getLayout() == Layout.PHONE_LANDSCAPE ? 0 :
                            layoutParams.topMargin);
        }

        if (tabViewBottomMargin == -1) {
            tabViewBottomMargin = calculateBottomMargin(view);
        }

        animateBottomMargin(view, tabViewBottomMargin, duration, 0);
        ViewPropertyAnimator animation = view.animate();
        animation.setDuration(duration);
        animation.setInterpolator(interpolator);
        animation.setListener(new AnimationListenerWrapper(listener));
        getArithmetics().animateScale(Axis.DRAGGING_AXIS, animation, scale);
        getArithmetics().animateScale(Axis.ORTHOGONAL_AXIS, animation, scale);
        getArithmetics().animatePosition(Axis.DRAGGING_AXIS, animation, view,
                tabItem.getTag().getPosition(), true);
        getArithmetics().animatePosition(Axis.ORTHOGONAL_AXIS, animation, view, 0, true);
        animation.setStartDelay(0);
        animation.start();
    }

    /**
     * Hides the tab switcher in an animated manner.
     */
    private void animateHideSwitcher() {
        dragHandler.setCallback(null);
        TabItemIterator iterator =
                new TabItemIterator.Builder(getTabSwitcher(), tabViewRecycler).create();
        TabItem tabItem;

        while ((tabItem = iterator.next()) != null) {
            if (tabItem.isInflated()) {
                animateHideSwitcher(tabItem,
                        tabItem.getIndex() == getModel().getSelectedTabIndex() ?
                                createHideSwitcherAnimationListener() : null);
            } else if (tabItem.getTab() == getModel().getSelectedTab()) {
                inflateAndUpdateView(tabItem, createHideSwitcherLayoutListener(tabItem));
            }
        }

        animateToolbarVisibility(getModel().areToolbarsShown() && getModel().isEmpty(), 0);
    }

    /**
     * Animates the position and size of a specific tab item in order to hide the tab switcher.
     *
     * @param tabItem
     *         The tab item, which should be animated, as an instance of the class {@link TabItem}.
     *         The tab item may not be null
     * @param listener
     *         The listener, which should be notified about the animation's progress, as an instance
     *         of the type {@link AnimatorListener} or null, if no listener should be notified
     */
    private void animateHideSwitcher(@NonNull final TabItem tabItem,
                                     @Nullable final AnimatorListener listener) {
        animateHideSwitcher(tabItem, hideSwitcherAnimationDuration,
                new AccelerateDecelerateInterpolator(), 0, listener);
    }

    /**
     * Animates the position and size of a specific tab item in order to hide the tab switcher.
     *
     * @param tabItem
     *         The tab item, which should be animated, as an instance of the class {@link TabItem}.
     *         The tab item may not be null
     * @param duration
     *         The duration of the animation in milliseconds as a {@link Long} value
     * @param interpolator
     *         The interpolator, which should be used by the animation, as an instance of the class
     *         {@link Interpolator}. The interpolator may not be null
     * @param delay
     *         The delay of the animation in milliseconds as a {@link Long} value
     * @param listener
     *         The listener, which should be notified about the animation's progress, as an instance
     *         of the type {@link AnimatorListener} or null, if no listener should be notified
     */
    private void animateHideSwitcher(@NonNull final TabItem tabItem, final long duration,
                                     @NonNull final Interpolator interpolator, final long delay,
                                     @Nullable final AnimatorListener listener) {
        View view = tabItem.getView();
        animateBottomMargin(view, -(tabInset + tabBorderWidth), duration, delay);
        ViewPropertyAnimator animation = view.animate();
        animation.setDuration(duration);
        animation.setInterpolator(interpolator);
        animation.setListener(new AnimationListenerWrapper(listener));
        getArithmetics().animateScale(Axis.DRAGGING_AXIS, animation, 1);
        getArithmetics().animateScale(Axis.ORTHOGONAL_AXIS, animation, 1);
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) view.getLayoutParams();
        getArithmetics().animatePosition(Axis.ORTHOGONAL_AXIS, animation, view,
                getTabSwitcher().getLayout() == Layout.PHONE_LANDSCAPE ? layoutParams.topMargin : 0,
                false);
        int selectedTabIndex = getModel().getSelectedTabIndex();

        if (tabItem.getIndex() < selectedTabIndex) {
            getArithmetics().animatePosition(Axis.DRAGGING_AXIS, animation, view,
                    getArithmetics().getSize(Axis.DRAGGING_AXIS, getTabSwitcher()), false);
        } else if (tabItem.getIndex() > selectedTabIndex) {
            getArithmetics().animatePosition(Axis.DRAGGING_AXIS, animation, view,
                    getTabSwitcher().getLayout() == Layout.PHONE_LANDSCAPE ? 0 :
                            layoutParams.topMargin, false);
        } else {
            getArithmetics().animatePosition(Axis.DRAGGING_AXIS, animation, view,
                    getTabSwitcher().getLayout() == Layout.PHONE_LANDSCAPE ? 0 :
                            layoutParams.topMargin, false);
        }

        animation.setStartDelay(delay);
        animation.start();
    }

    /**
     * Animates the position, size and alpha of a specific tab item in order to swipe it
     * orthogonally.
     *
     * @param tabItem
     *         The tab item, which should be animated, as an instance of the class {@link TabItem}.
     *         The tab item may not be null
     * @param remove
     *         True, if the tab should be removed after the animation has finished, false otherwise
     * @param delay
     *         The delay after which the animation should be started in milliseconds as a {@link
     *         Long} value
     * @param swipeAnimation
     *         The animation, which should be used, as an instance of the class {@link
     *         SwipeAnimation}. The animation may not be null
     * @param listener
     *         The listener, which should be notified about the progress of the animation, as an
     *         instance of the type {@link AnimatorListener} or null, if no listener should be
     *         notified
     */
    private void animateSwipe(@NonNull final TabItem tabItem, final boolean remove,
                              final long delay, @NonNull final SwipeAnimation swipeAnimation,
                              @Nullable final AnimatorListener listener) {
        View view = tabItem.getView();
        float currentScale = getArithmetics().getScale(view, true);
        float swipePosition = calculateSwipePosition();
        float targetPosition = remove ?
                (swipeAnimation.getDirection() == SwipeDirection.LEFT ? -1 * swipePosition :
                        swipePosition) : 0;
        float currentPosition = getArithmetics().getPosition(Axis.ORTHOGONAL_AXIS, view);
        float distance = Math.abs(targetPosition - currentPosition);
        long animationDuration = swipeAnimation.getDuration() != -1 ? swipeAnimation.getDuration() :
                Math.round(swipeAnimationDuration * (distance / swipePosition));
        ViewPropertyAnimator animation = view.animate();
        animation.setInterpolator(
                swipeAnimation.getInterpolator() != null ? swipeAnimation.getInterpolator() :
                        new AccelerateDecelerateInterpolator());
        animation.setListener(new AnimationListenerWrapper(listener));
        animation.setDuration(animationDuration);
        getArithmetics()
                .animatePosition(Axis.ORTHOGONAL_AXIS, animation, view, targetPosition, true);
        getArithmetics().animateScale(Axis.ORTHOGONAL_AXIS, animation,
                remove ? swipedTabScale * currentScale : currentScale);
        getArithmetics().animateScale(Axis.DRAGGING_AXIS, animation,
                remove ? swipedTabScale * currentScale : currentScale);
        animation.alpha(remove ? swipedTabAlpha : 1);
        animation.setStartDelay(delay);
        animation.start();
    }

    /**
     * Animates the removal of a specific tab item.
     *
     * @param removedTabItem
     *         The tab item, which should be animated, as an instance of the class {@link TabItem}.
     *         The tab item may not be null
     * @param swipeAnimation
     *         The animation, which should be used, as an instance of the class {@link
     *         SwipeAnimation}. The animation may not be null
     */
    private void animateRemove(@NonNull final TabItem removedTabItem,
                               @NonNull final SwipeAnimation swipeAnimation) {
        View view = removedTabItem.getView();
        getArithmetics().setPivot(Axis.DRAGGING_AXIS, view,
                getArithmetics().getPivot(Axis.DRAGGING_AXIS, view, DragState.SWIPE));
        getArithmetics().setPivot(Axis.ORTHOGONAL_AXIS, view,
                getArithmetics().getPivot(Axis.ORTHOGONAL_AXIS, view, DragState.SWIPE));
        animateSwipe(removedTabItem, true, 0, swipeAnimation,
                createRemoveAnimationListener(removedTabItem));
    }

    /**
     * Animates the position of a specific tab item in order to relocate it.
     *
     * @param tabItem
     *         The tab item, which should be animated, as an instance of the class {@link TabItem}.
     *         The tab item may not be null
     * @param position
     *         The position, the tab should be relocated to, in pixels as a {@link Float} value
     * @param tag
     *         The tag, which should be applied to the given tab item, as an instance of the class
     *         {@link Tag} or null, if no tag should be applied
     * @param delay
     *         The delay of the relocate animation in milliseconds as a {@link Long} value
     * @param listener
     *         The listener, which should be notified about the progress of the relocate animation,
     *         as an instance of the type {@link AnimatorListener} or null, if no listener should be
     *         notified
     */
    private void animateRelocate(@NonNull final TabItem tabItem, final float position,
                                 @Nullable final Tag tag, final long delay,
                                 @Nullable final AnimatorListener listener) {
        if (tag != null) {
            tabItem.getView().setTag(R.id.tag_properties, tag);
            tabItem.setTag(tag);
        }

        View view = tabItem.getView();
        ViewPropertyAnimator animation = view.animate();
        animation.setListener(new AnimationListenerWrapper(listener));
        animation.setInterpolator(new AccelerateDecelerateInterpolator());
        animation.setDuration(relocateAnimationDuration);
        getArithmetics().animatePosition(Axis.DRAGGING_AXIS, animation, view, position, true);
        animation.setStartDelay(delay);
        animation.start();
    }

    /**
     * Animates reverting an overshoot at the start.
     */
    private void animateRevertStartOvershoot() {
        boolean tilted = animateTilt(new AccelerateInterpolator(), maxStartOvershootAngle,
                createRevertStartOvershootAnimationListener());

        if (!tilted) {
            animateRevertStartOvershoot(new AccelerateDecelerateInterpolator());
        }
    }

    /**
     * Animates reverting an overshoot at the start using a specific interpolator.
     *
     * @param interpolator
     *         The interpolator, which should be used by the animation, as an instance of the type
     *         {@link Interpolator}. The interpolator may not be null
     */
    private void animateRevertStartOvershoot(@NonNull final Interpolator interpolator) {
        TabItem tabItem = TabItem.create(getTabSwitcher(), tabViewRecycler, 0);
        View view = tabItem.getView();
        getArithmetics().setPivot(Axis.DRAGGING_AXIS, view,
                getArithmetics().getPivot(Axis.DRAGGING_AXIS, view, DragState.NONE));
        getArithmetics().setPivot(Axis.ORTHOGONAL_AXIS, view,
                getArithmetics().getPivot(Axis.ORTHOGONAL_AXIS, view, DragState.NONE));
        float position = getArithmetics().getPosition(Axis.DRAGGING_AXIS, view);
        float targetPosition = tabItem.getTag().getPosition();
        final float startPosition = getArithmetics().getPosition(Axis.DRAGGING_AXIS, view);
        ValueAnimator animation = ValueAnimator.ofFloat(targetPosition - position);
        animation.setDuration(Math.round(revertOvershootAnimationDuration * Math.abs(
                (targetPosition - position) /
                        (float) (getStackedTabCount() * getStackedTabSpacing()))));
        animation.addListener(new AnimationListenerWrapper(null));
        animation.setInterpolator(interpolator);
        animation.setStartDelay(0);
        animation.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {

            @Override
            public void onAnimationUpdate(final ValueAnimator animation) {
                TabItemIterator iterator =
                        new TabItemIterator.Builder(getTabSwitcher(), tabViewRecycler).create();
                TabItem tabItem;

                while ((tabItem = iterator.next()) != null) {
                    if (tabItem.getIndex() == 0) {
                        View view = tabItem.getView();
                        getArithmetics().setPosition(Axis.DRAGGING_AXIS, view,
                                startPosition + (float) animation.getAnimatedValue());
                    } else if (tabItem.isInflated()) {
                        View firstView = iterator.first().getView();
                        View view = tabItem.getView();
                        view.setVisibility(
                                getArithmetics().getPosition(Axis.DRAGGING_AXIS, firstView) <=
                                        getArithmetics().getPosition(Axis.DRAGGING_AXIS, view) ?
                                        View.INVISIBLE : View.VISIBLE);
                    }
                }
            }

        });

        animation.start();
    }

    /**
     * Animates reverting an overshoot at the end.
     */
    private void animateRevertEndOvershoot() {
        animateTilt(new AccelerateDecelerateInterpolator(), maxEndOvershootAngle, null);
    }

    /**
     * Animates to rotation of all tabs to be reset to normal.
     *
     * @param interpolator
     *         The interpolator, which should be used by the animation, as an instance of the type
     *         {@link Interpolator}. The interpolator may not be null
     * @param maxAngle
     *         The angle, the tabs may be rotated by at maximum, in degrees as a {@link Float}
     *         value
     * @param listener
     *         The listener, which should be notified about the animation's progress, as an instance
     *         of the type {@link AnimatorListener} or null, if no listener should be notified
     * @return True, if at least one tab was animated, false otherwise
     */
    private boolean animateTilt(@NonNull final Interpolator interpolator, final float maxAngle,
                                @Nullable final AnimatorListener listener) {
        TabItemIterator iterator =
                new TabItemIterator.Builder(getTabSwitcher(), tabViewRecycler).reverse(true)
                        .create();
        TabItem tabItem;
        boolean result = false;

        while ((tabItem = iterator.next()) != null) {
            if (tabItem.isInflated()) {
                View view = tabItem.getView();

                if (getArithmetics().getRotation(Axis.ORTHOGONAL_AXIS, view) != 0) {
                    ViewPropertyAnimator animation = view.animate();
                    animation.setListener(new AnimationListenerWrapper(
                            createRevertOvershootAnimationListener(view,
                                    !result ? listener : null)));
                    animation.setDuration(Math.round(revertOvershootAnimationDuration *
                            (Math.abs(getArithmetics().getRotation(Axis.ORTHOGONAL_AXIS, view)) /
                                    maxAngle)));
                    animation.setInterpolator(interpolator);
                    getArithmetics().animateRotation(Axis.ORTHOGONAL_AXIS, animation, 0);
                    animation.setStartDelay(0);
                    animation.start();
                    result = true;
                }
            }
        }

        return result;
    }

    /**
     * Starts a reveal animation to add a specific tab.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, which should be added, as an instance of
     *         the class {@link TabItem}. The tab item may not be null
     * @param revealAnimation
     *         The reveal animation, which should be started, as an instance of the class {@link
     *         RevealAnimation}. The reveal animation may not be null
     */
    private void animateReveal(@NonNull final TabItem tabItem,
                               @NonNull final RevealAnimation revealAnimation) {
        tabViewBottomMargin = -1;
        recyclerAdapter.clearCachedPreviews();
        dragHandler.setCallback(null);
        View view = tabItem.getView();
        ViewPropertyAnimator animation = view.animate();
        animation.setInterpolator(
                revealAnimation.getInterpolator() != null ? revealAnimation.getInterpolator() :
                        new AccelerateDecelerateInterpolator());
        animation.setListener(new AnimationListenerWrapper(createHideSwitcherAnimationListener()));
        animation.setStartDelay(0);
        animation.setDuration(revealAnimation.getDuration() != -1 ? revealAnimation.getDuration() :
                revealAnimationDuration);
        getArithmetics().animateScale(Axis.DRAGGING_AXIS, animation, 1);
        getArithmetics().animateScale(Axis.ORTHOGONAL_AXIS, animation, 1);
        animation.start();
        animateToolbarVisibility(getModel().areToolbarsShown() && getModel().isEmpty(), 0);
    }

    /**
     * Starts a peek animation to add a specific tab.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, which should be added, as an instance of
     *         the class {@link TabItem}. The tab item may not be null
     * @param duration
     *         The duration of the animation in milliseconds as a {@link Long} value
     * @param interpolator
     *         The interpolator, which should be used by the animation, as an instance of the type
     *         {@link Interpolator}. The interpolator may not be null
     * @param peekPosition
     *         The position on the dragging axis, the tab should be moved to, in pixels as a {@link
     *         Float} value
     * @param peekAnimation
     *         The peek animation, which has been used to add the tab, as an instance of the class
     *         {@link PeekAnimation}. The peek animation may not be null
     */
    private void animatePeek(@NonNull final TabItem tabItem, final long duration,
                             @NonNull final Interpolator interpolator, final float peekPosition,
                             @NonNull final PeekAnimation peekAnimation) {
        PhoneTabViewHolder viewHolder = (PhoneTabViewHolder) tabItem.getViewHolder();
        viewHolder.closeButton.setVisibility(View.GONE);
        View view = tabItem.getView();
        float x = peekAnimation.getX();
        float y = peekAnimation.getY() + tabTitleContainerHeight;
        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) view.getLayoutParams();
        view.setAlpha(1f);
        getArithmetics().setPivot(Axis.X_AXIS, view, x);
        getArithmetics().setPivot(Axis.Y_AXIS, view, y);
        view.setX(layoutParams.leftMargin);
        view.setY(layoutParams.topMargin);
        getArithmetics().setScale(Axis.DRAGGING_AXIS, view, 0);
        getArithmetics().setScale(Axis.ORTHOGONAL_AXIS, view, 0);
        ViewPropertyAnimator animation = view.animate();
        animation.setInterpolator(interpolator);
        animation.setListener(
                new AnimationListenerWrapper(createPeekAnimationListener(tabItem, peekAnimation)));
        animation.setStartDelay(0);
        animation.setDuration(duration);
        getArithmetics().animateScale(Axis.DRAGGING_AXIS, animation, 1);
        getArithmetics().animateScale(Axis.ORTHOGONAL_AXIS, animation, 1);
        getArithmetics().animatePosition(Axis.DRAGGING_AXIS, animation, view, peekPosition, true);
        animation.start();
        int selectedTabIndex = getModel().getSelectedTabIndex();
        TabItem selectedTabItem = TabItem.create(getModel(), tabViewRecycler, selectedTabIndex);
        tabViewRecycler.inflate(selectedTabItem);
        selectedTabItem.getTag().setPosition(0);
        PhoneTabViewHolder selectedTabViewHolder =
                (PhoneTabViewHolder) selectedTabItem.getViewHolder();
        selectedTabViewHolder.closeButton.setVisibility(View.GONE);
        animateShowSwitcher(selectedTabItem, duration, interpolator,
                createZoomOutAnimationListener(selectedTabItem, peekAnimation));
    }

    /**
     * Creates and returns a layout listener, which allows to animate the position and size of a tab
     * in order to show the tab switcher, once its view has been inflated.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, whose view should be animated, as an
     *         instance of the class {@link TabItem}. The tab item may not be null
     * @return The listener, which has been created, as an instance of the type {@link
     * OnGlobalLayoutListener}. The listener may not be null
     */
    @NonNull
    private OnGlobalLayoutListener createShowSwitcherLayoutListener(
            @NonNull final TabItem tabItem) {
        return new OnGlobalLayoutListener() {

            @Override
            public void onGlobalLayout() {
                animateShowSwitcher(tabItem, createUpdateViewAnimationListener(tabItem));
            }

        };
    }

    /**
     * Creates and returns a layout listener, which allows to animate the position and size of a tab
     * in order to hide the tab switcher, once its view has been inflated.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, whose view should be animated, as an
     *         instance of the class {@link TabItem}. The tab item may not be null
     * @return The listener, which has been created, as an instance of the type {@link
     * OnGlobalLayoutListener}. The listener may not be null
     */
    @NonNull
    private OnGlobalLayoutListener createHideSwitcherLayoutListener(
            @NonNull final TabItem tabItem) {
        return new OnGlobalLayoutListener() {

            @Override
            public void onGlobalLayout() {
                animateHideSwitcher(tabItem,
                        tabItem.getIndex() == getModel().getSelectedTabIndex() ?
                                createHideSwitcherAnimationListener() : null);
            }

        };
    }

    /**
     * Creates and returns a layout listener, which allows to remove a tab, once its view has been
     * inflated.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, which should be removed, as an instance
     *         of the class {@link TabItem}. The tab item may not be null
     * @param swipeAnimation
     *         The animation, which should be used, as an instance of the class {@link
     *         SwipeAnimation}. The animation may not be null
     * @return The listener, which has been created, as an instance of the type {@link
     * OnGlobalLayoutListener}. The listener may not be null
     */
    @NonNull
    private OnGlobalLayoutListener createRemoveLayoutListener(@NonNull final TabItem tabItem,
                                                              @NonNull final SwipeAnimation swipeAnimation) {
        return new OnGlobalLayoutListener() {

            @Override
            public void onGlobalLayout() {
                animateRemove(tabItem, swipeAnimation);
            }

        };
    }

    /**
     * Creates and returns a layout listener, which allows to relocate a tab, once its view has been
     * inflated.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, which should be relocated, as an instance
     *         of the class {@link TabItem}. The tab item may not be null
     * @param position
     *         The position, the tab should be relocated to, in pixels as a {@link Float} value
     * @param tag
     *         The tag, which should be applied to the given tab item, as an instance of the class
     *         {@link Tag} or null, if no tag should be applied
     * @param delay
     *         The delay of the relocate animation in milliseconds as a {@link Long} value
     * @param listener
     *         The listener, which should be notified about the progress of the relocate animation,
     *         as an instance of the type {@link AnimatorListener} or null, if no listener should be
     *         notified
     * @return The listener, which has been created, as an instance of the class {@link
     * OnGlobalLayoutListener}. The listener may not be null
     */
    @NonNull
    private OnGlobalLayoutListener createRelocateLayoutListener(@NonNull final TabItem tabItem,
                                                                final float position,
                                                                @Nullable final Tag tag,
                                                                final long delay,
                                                                @Nullable final AnimatorListener listener) {
        return new OnGlobalLayoutListener() {

            @Override
            public void onGlobalLayout() {
                animateRelocate(tabItem, position, tag, delay, listener);
            }

        };
    }

    /**
     * Creates and returns a layout listener, which allows to show a tab as the currently selected
     * one, once it view has been inflated.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, which has been added, as an instance of
     *         the class {@link TabItem}. The tab item may not be null
     * @return The listener, which has been created, as an instance of the type {@link
     * OnGlobalLayoutListener}. The listener may not be null
     */
    @NonNull
    private OnGlobalLayoutListener createAddSelectedTabLayoutListener(
            @NonNull final TabItem tabItem) {
        return new OnGlobalLayoutListener() {

            @Override
            public void onGlobalLayout() {
                View view = tabItem.getView();
                FrameLayout.LayoutParams layoutParams =
                        (FrameLayout.LayoutParams) view.getLayoutParams();
                view.setAlpha(1f);
                getArithmetics().setPivot(Axis.DRAGGING_AXIS, view,
                        getArithmetics().getPivot(Axis.DRAGGING_AXIS, view, DragState.NONE));
                getArithmetics().setPivot(Axis.ORTHOGONAL_AXIS, view,
                        getArithmetics().getPivot(Axis.ORTHOGONAL_AXIS, view, DragState.NONE));
                view.setX(layoutParams.leftMargin);
                view.setY(layoutParams.topMargin);
                getArithmetics().setScale(Axis.DRAGGING_AXIS, view, 1);
                getArithmetics().setScale(Axis.ORTHOGONAL_AXIS, view, 1);
            }

        };
    }

    /**
     * Creates and returns a layout listener, which allows to start a reveal animation to add a tab,
     * once its view has been inflated.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, which should be added, as an instance of
     *         the class {@link TabItem}. The tab item may not be null
     * @param revealAnimation
     *         The reveal animation, which should be started, as an instance of the class {@link
     *         RevealAnimation}. The reveal animation may not be null
     * @return The listener, which has been created, as an instance of the type {@link
     * OnGlobalLayoutListener}. The listener may not be null
     */
    @NonNull
    private OnGlobalLayoutListener createRevealLayoutListener(@NonNull final TabItem tabItem,
                                                              @NonNull final RevealAnimation revealAnimation) {
        return new OnGlobalLayoutListener() {

            @Override
            public void onGlobalLayout() {
                View view = tabItem.getView();
                float x = revealAnimation.getX();
                float y = revealAnimation.getY() + tabTitleContainerHeight;
                FrameLayout.LayoutParams layoutParams =
                        (FrameLayout.LayoutParams) view.getLayoutParams();
                view.setAlpha(1f);
                getArithmetics().setPivot(Axis.X_AXIS, view, x);
                getArithmetics().setPivot(Axis.Y_AXIS, view, y);
                view.setX(layoutParams.leftMargin);
                view.setY(layoutParams.topMargin);
                getArithmetics().setScale(Axis.DRAGGING_AXIS, view, 0);
                getArithmetics().setScale(Axis.ORTHOGONAL_AXIS, view, 0);
                animateReveal(tabItem, revealAnimation);
            }

        };
    }

    /**
     * Creates and returns a layout listener, which allows to start a peek animation to add a tab,
     * once its view has been inflated.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, which should be added, as an instance of
     *         the class {@link TabItem}. The tab item may not be null
     * @param peekAnimation
     *         The peek animation, which should be started, as an instance of the class {@link
     *         PeekAnimation}. The peek animation may not be null
     * @return The listener, which has been created, as an instance of the type {@link
     * OnGlobalLayoutListener}. The listener may not be null
     */
    private OnGlobalLayoutListener createPeekLayoutListener(@NonNull final TabItem tabItem,
                                                            @NonNull final PeekAnimation peekAnimation) {
        return new OnGlobalLayoutListener() {

            @Override
            public void onGlobalLayout() {
                long totalDuration =
                        peekAnimation.getDuration() != -1 ? peekAnimation.getDuration() :
                                peekAnimationDuration;
                long duration = totalDuration / 3;
                Interpolator interpolator =
                        peekAnimation.getInterpolator() != null ? peekAnimation.getInterpolator() :
                                new AccelerateDecelerateInterpolator();
                Toolbar[] toolbars = getToolbars();
                float peekPosition =
                        (getArithmetics().getSize(Axis.DRAGGING_AXIS, getTabSwitcher()) -
                                getArithmetics().getPadding(Axis.DRAGGING_AXIS, Gravity.START,
                                        getTabSwitcher()) -
                                (getTabSwitcher().getLayout() == Layout.PHONE_PORTRAIT &&
                                        getTabSwitcher().areToolbarsShown() && toolbars != null ?
                                        toolbars[0].getHeight() : 0)) * 0.66f;
                animatePeek(tabItem, duration, interpolator, peekPosition, peekAnimation);
            }

        };
    }

    /**
     * Creates and returns a layout listener, which allows to start a swipe animations to add
     * several tabs, once their views have been inflated.
     *
     * @param addedTabItems
     *         An array, which contains the tab items, which correspond to the tabs, which should be
     *         added, as an array of the type {@link TabItem}. The array may not be null
     * @param swipeAnimation
     *         The swipe animation, which should be started, as an instance of the class {@link
     *         SwipeAnimation}. The swipe animation may not be null
     * @return The listener, which has been created, as an instance of the type {@link
     * OnGlobalLayoutListener}. The listener may not be null
     */
    @NonNull
    private OnGlobalLayoutListener createSwipeLayoutListener(@NonNull final TabItem[] addedTabItems,
                                                             @NonNull final SwipeAnimation swipeAnimation) {
        return new OnGlobalLayoutListener() {

            @Override
            public void onGlobalLayout() {
                int count = getModel().getCount();
                float previousAttachedPosition =
                        calculateAttachedPosition(count - addedTabItems.length);
                float attachedPosition = calculateAttachedPosition(count);
                TabItem[] tabItems;

                if (count - addedTabItems.length == 0) {
                    tabItems = calculateInitialTabItems(-1, -1);
                } else {
                    TabItem firstAddedTabItem = addedTabItems[0];
                    int index = firstAddedTabItem.getIndex();
                    boolean isReferencingPredecessor = index > 0;
                    int referenceIndex = isReferencingPredecessor ? index - 1 :
                            (index + addedTabItems.length - 1 < count - 1 ?
                                    index + addedTabItems.length : -1);
                    TabItem referenceTabItem = referenceIndex != -1 ?
                            TabItem.create(getTabSwitcher(), tabViewRecycler, referenceIndex) :
                            null;
                    State state =
                            referenceTabItem != null ? referenceTabItem.getTag().getState() : null;

                    if (state == null || state == State.STACKED_START) {
                        tabItems = relocateWhenAddingStackedTabs(true, addedTabItems);
                    } else if (state == State.STACKED_END) {
                        tabItems = relocateWhenAddingStackedTabs(false, addedTabItems);
                    } else if (state == State.FLOATING ||
                            (state == State.STACKED_START_ATOP && (index > 0 || count <= 2))) {
                        tabItems = relocateWhenAddingFloatingTabs(addedTabItems, referenceTabItem,
                                isReferencingPredecessor, attachedPosition,
                                attachedPosition != previousAttachedPosition);
                    } else {
                        tabItems = relocateWhenAddingHiddenTabs(addedTabItems, referenceTabItem);
                    }
                }

                Tag previousTag = null;

                for (TabItem tabItem : tabItems) {
                    Tag tag = tabItem.getTag();

                    if (previousTag == null || tag.getPosition() != previousTag.getPosition()) {
                        createBottomMarginLayoutListener(tabItem).onGlobalLayout();
                        View view = tabItem.getView();
                        view.setTag(R.id.tag_properties, tag);
                        view.setAlpha(swipedTabAlpha);
                        float swipePosition = calculateSwipePosition();
                        float scale = getArithmetics().getScale(view, true);
                        getArithmetics().setPivot(Axis.DRAGGING_AXIS, view, getArithmetics()
                                .getPivot(Axis.DRAGGING_AXIS, view, DragState.NONE));
                        getArithmetics().setPivot(Axis.ORTHOGONAL_AXIS, view, getArithmetics()
                                .getPivot(Axis.ORTHOGONAL_AXIS, view, DragState.NONE));
                        getArithmetics().setPosition(Axis.DRAGGING_AXIS, view, tag.getPosition());
                        getArithmetics().setPosition(Axis.ORTHOGONAL_AXIS, view,
                                swipeAnimation.getDirection() == SwipeDirection.LEFT ?
                                        -1 * swipePosition : swipePosition);
                        getArithmetics().setScale(Axis.DRAGGING_AXIS, view, scale);
                        getArithmetics().setScale(Axis.ORTHOGONAL_AXIS, view, scale);
                        getArithmetics().setPivot(Axis.DRAGGING_AXIS, view, getArithmetics()
                                .getPivot(Axis.DRAGGING_AXIS, view, DragState.SWIPE));
                        getArithmetics().setPivot(Axis.ORTHOGONAL_AXIS, view, getArithmetics()
                                .getPivot(Axis.ORTHOGONAL_AXIS, view, DragState.SWIPE));
                        getArithmetics().setScale(Axis.DRAGGING_AXIS, view, swipedTabScale * scale);
                        getArithmetics()
                                .setScale(Axis.ORTHOGONAL_AXIS, view, swipedTabScale * scale);
                        animateSwipe(tabItem, false, 0, swipeAnimation,
                                createSwipeAnimationListener(tabItem));
                    } else {
                        tabViewRecycler.remove(tabItem);
                    }

                    previousTag = tag;
                }
            }

        };
    }

    /**
     * Creates and returns a layout listener, which allows to adapt the bottom margin of a tab, once
     * its view has been inflated.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, whose view should be adapted, as an
     *         instance of the class {@link TabItem}. The tab item may not be null
     * @return The layout listener, which has been created, as an instance of the type {@link
     * OnGlobalLayoutListener}. The layout listener may not be null
     */
    private OnGlobalLayoutListener createBottomMarginLayoutListener(
            @NonNull final TabItem tabItem) {
        return new OnGlobalLayoutListener() {

            @Override
            public void onGlobalLayout() {
                View view = tabItem.getView();

                if (tabViewBottomMargin == -1) {
                    tabViewBottomMargin = calculateBottomMargin(view);
                }

                FrameLayout.LayoutParams layoutParams =
                        (FrameLayout.LayoutParams) view.getLayoutParams();
                layoutParams.bottomMargin = tabViewBottomMargin;
                view.setLayoutParams(layoutParams);
            }

        };
    }

    /**
     * Creates and returns a layout listener, which allows to adapt the size and position of a tab,
     * once its view has been inflated.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, whose view should be adapted, as an
     *         instance of the class {@link TabItem}. The tab item may not be null
     * @param layoutListener
     *         The layout lister, which should be notified, when the created listener is invoked, as
     *         an instance of the type {@link OnGlobalLayoutListener} or null, if no listener should
     *         be notified
     * @return The layout listener, which has been created, as an instance of the type {@link
     * OnGlobalLayoutListener}. The layout listener may not be null
     */
    @NonNull
    private OnGlobalLayoutListener createInflateViewLayoutListener(@NonNull final TabItem tabItem,
                                                                   @Nullable final OnGlobalLayoutListener layoutListener) {
        return new OnGlobalLayoutListener() {

            @Override
            public void onGlobalLayout() {
                adaptViewSize(tabItem);
                updateView(tabItem);

                if (layoutListener != null) {
                    layoutListener.onGlobalLayout();
                }
            }

        };
    }

    /**
     * Creates and returns an animation listener, which allows to update the view, which is used to
     * visualize a specific tab, when an animation has been finished.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, whose view should be updated, as an
     *         instance of the class {@link TabItem}. The tab item may not be null
     * @return The animation listener, which has been created, as an instance of the type {@link
     * AnimatorListener}. The listener may not be null
     */
    @NonNull
    private AnimatorListener createUpdateViewAnimationListener(@NonNull final TabItem tabItem) {
        return new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(final Animator animation) {
                super.onAnimationEnd(animation);
                inflateOrRemoveView(tabItem);
            }

        };
    }

    /**
     * Creates and returns an animation listener, which allows to inflate or remove the views, which
     * are used to visualize tabs, when an animation, which is used to hide the tab switcher,
     * has been finished.
     *
     * @return The animation listener, which has been created, as an instance of the type {@link
     * AnimatorListener}. The listener may not be null
     */
    @NonNull
    private AnimatorListener createHideSwitcherAnimationListener() {
        return new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(final Animator animation) {
                super.onAnimationEnd(animation);
                AbstractTabItemIterator iterator =
                        new TabItemIterator.Builder(getTabSwitcher(), tabViewRecycler).create();
                TabItem tabItem;

                while ((tabItem = iterator.next()) != null) {
                    if (tabItem.getTab() == getModel().getSelectedTab()) {
                        Pair<View, Boolean> pair = tabViewRecycler.inflate(tabItem);
                        View view = pair.first;
                        FrameLayout.LayoutParams layoutParams =
                                (FrameLayout.LayoutParams) view.getLayoutParams();
                        view.setAlpha(1f);
                        getArithmetics().setScale(Axis.DRAGGING_AXIS, view, 1);
                        getArithmetics().setScale(Axis.ORTHOGONAL_AXIS, view, 1);
                        view.setX(layoutParams.leftMargin);
                        view.setY(layoutParams.topMargin);
                    } else {
                        tabViewRecycler.remove(tabItem);
                    }
                }

                tabViewRecycler.clearCache();
                recyclerAdapter.clearCachedPreviews();
                tabViewBottomMargin = -1;
            }

        };
    }

    /**
     * Creates and returns an animation listener, which allows to remove all tabs, when the
     * animation, which is used to swipe all tabs, has been finished.
     *
     * @return The animation listener, which has been created, as an instance of the type {@link
     * AnimatorListener}. The listener may not be null
     */
    @NonNull
    private AnimatorListener createClearAnimationListener() {
        return new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(final Animator animation) {
                super.onAnimationEnd(animation);
                tabViewRecycler.removeAll();
                animateToolbarVisibility(getModel().areToolbarsShown(), 0);
            }

        };
    }

    /**
     * Creates and returns a listener, which allows to handle, when a tab has been swiped, but was
     * not removed.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, which has been swiped, as an instance of
     *         the class {@link TabItem}. The tab item may not be null
     * @return The listener, which has been created, as an instance of the type {@link
     * AnimatorListener}. The listener may not be null
     */
    @NonNull
    private AnimatorListener createSwipeAnimationListener(@NonNull final TabItem tabItem) {
        return new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(final Animator animation) {
                super.onAnimationEnd(animation);
                inflateOrRemoveView(tabItem);
                View view = tabItem.getView();
                adaptStackOnSwipeAborted(tabItem, tabItem.getIndex() + 1);
                tabItem.getTag().setClosing(false);
                getArithmetics().setPivot(Axis.DRAGGING_AXIS, view,
                        getArithmetics().getPivot(Axis.DRAGGING_AXIS, view, DragState.NONE));
                animateToolbarVisibility(true, 0);
            }

        };
    }

    /**
     * Creates and returns a listener, which allows to relocate all previous tabs, when a tab has
     * been removed.
     *
     * @param removedTabItem
     *         The tab item, which corresponds to the tab, which has been removed, as an instance of
     *         the class {@link TabItem}. The tab item may not be null
     * @return The listener, which has been created, as an instance of the type {@link
     * AnimatorListener}. The listener may not be null
     */
    @NonNull
    private AnimatorListener createRemoveAnimationListener(@NonNull final TabItem removedTabItem) {
        return new AnimatorListenerAdapter() {

            @Override
            public void onAnimationStart(final Animator animation) {
                super.onAnimationStart(animation);

                if (getModel().isEmpty()) {
                    animateToolbarVisibility(getModel().areToolbarsShown(), 0);
                }

                float previousAttachedPosition =
                        calculateAttachedPosition(getModel().getCount() + 1);
                float attachedPosition = calculateAttachedPosition(getModel().getCount());
                State state = removedTabItem.getTag().getState();

                if (state == State.STACKED_END) {
                    relocateWhenRemovingStackedTab(removedTabItem, false);
                } else if (state == State.STACKED_START) {
                    relocateWhenRemovingStackedTab(removedTabItem, true);
                } else if (state == State.FLOATING || state == State.STACKED_START_ATOP) {
                    relocateWhenRemovingFloatingTab(removedTabItem, attachedPosition,
                            previousAttachedPosition != attachedPosition);
                }
            }

            @Override
            public void onAnimationEnd(final Animator animation) {
                super.onAnimationEnd(animation);
                tabViewRecycler.remove(removedTabItem);
            }

        };
    }

    /**
     * Creates and returns an animation listener, which allows to update or remove the view, which
     * is used to visualize a tab, when the animation, which has been used to relocate it, has been
     * ended.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, which has been relocated, as an instance
     *         of the class {@link TabItem}. The tab item may not be null
     * @return The listener, which has been created, as an instance of the type {@link
     * AnimatorListener}. The listener may not be null
     */
    @NonNull
    private AnimatorListener createRelocateAnimationListener(@NonNull final TabItem tabItem) {
        return new AnimatorListenerAdapter() {

            @Override
            public void onAnimationStart(final Animator animation) {
                super.onAnimationStart(animation);
                tabItem.getView().setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(final Animator animation) {
                super.onAnimationEnd(animation);

                if (tabItem.getTag().getState() == State.STACKED_START_ATOP) {
                    adaptStackOnSwipeAborted(tabItem, tabItem.getIndex() + 1);
                }

                if (tabItem.isVisible()) {
                    updateView(tabItem);
                } else {
                    tabViewRecycler.remove(tabItem);
                }
            }

        };
    }

    /**
     * Creates and returns an animation listener, which allows to adapt the pivot of a specific
     * view, when an animation, which reverted an overshoot, has been ended.
     *
     * @param view
     *         The view, whose pivot should be adapted, as an instance of the class {@link View}.
     *         The view may not be null
     * @param listener
     *         The listener, which should be notified about the animation's progress, as an instance
     *         of the type {@link AnimatorListener} or null, if no listener should be notified
     * @return The listener, which has been created, as an instance of the type {@link
     * AnimatorListener}. The listener may not be null
     */
    @NonNull
    private AnimatorListener createRevertOvershootAnimationListener(@NonNull final View view,
                                                                    @Nullable final AnimatorListener listener) {
        return new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(final Animator animation) {
                super.onAnimationEnd(animation);
                getArithmetics().setPivot(Axis.DRAGGING_AXIS, view,
                        getArithmetics().getPivot(Axis.DRAGGING_AXIS, view, DragState.NONE));
                getArithmetics().setPivot(Axis.ORTHOGONAL_AXIS, view,
                        getArithmetics().getPivot(Axis.DRAGGING_AXIS, view, DragState.NONE));

                if (listener != null) {
                    listener.onAnimationEnd(animation);
                }
            }

        };
    }

    /**
     * Creates and returns an animation listener, which allows to revert an overshoot at the start,
     * when an animation has been ended.
     *
     * @return The listener, which has been created, as an instance of the type {@link
     * AnimatorListener}. The listener may not be null
     */
    @NonNull
    private AnimatorListener createRevertStartOvershootAnimationListener() {
        return new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(final Animator animation) {
                super.onAnimationEnd(animation);
                animateRevertStartOvershoot(new DecelerateInterpolator());
            }

        };
    }

    /**
     * Creates and returns an animation listener, which allows to hide a tab, which has been added
     * by using a peek animation, when the animation has been ended.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, which has been added by using the peek
     *         animation, as an instance of the class {@link TabItem}. The tab item may not be null
     * @param peekAnimation
     *         The peek animation as an instance of the class {@link PeekAnimation}. The peek
     *         animation may not be null
     * @return The listener, which has been created, as an instance of the type {@link
     * AnimatorListener}. The listener may not be null
     */
    @NonNull
    private AnimatorListener createPeekAnimationListener(@NonNull final TabItem tabItem,
                                                         @NonNull final PeekAnimation peekAnimation) {
        return new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(final Animator animation) {
                super.onAnimationEnd(animation);
                long totalDuration =
                        peekAnimation.getDuration() != -1 ? peekAnimation.getDuration() :
                                peekAnimationDuration;
                long duration = totalDuration / 3;
                Interpolator interpolator =
                        peekAnimation.getInterpolator() != null ? peekAnimation.getInterpolator() :
                                new AccelerateDecelerateInterpolator();
                View view = tabItem.getView();
                getArithmetics().setPivot(Axis.DRAGGING_AXIS, view, tabTitleContainerHeight);
                getArithmetics().setPivot(Axis.ORTHOGONAL_AXIS, view,
                        getArithmetics().getSize(Axis.ORTHOGONAL_AXIS, view) / 2f);
                ViewPropertyAnimator animator = view.animate();
                animator.setDuration(duration);
                animator.setStartDelay(duration);
                animator.setInterpolator(interpolator);
                animator.setListener(
                        new AnimationListenerWrapper(createRevertPeekAnimationListener(tabItem)));
                animator.alpha(0);
                getArithmetics().animatePosition(Axis.DRAGGING_AXIS, animator, view,
                        getArithmetics().getPosition(Axis.DRAGGING_AXIS, view) * 1.5f, false);
                getArithmetics().animateScale(Axis.DRAGGING_AXIS, animator, 0);
                getArithmetics().animateScale(Axis.ORTHOGONAL_AXIS, animator, 0);
                animator.start();
            }

        };
    }

    /**
     * Creates and returns an animation listener, which allows to remove the view of a tab, which
     * has been added by using a peek animation, when the animation, which reverts the peek
     * animation, has been ended.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, which has been added by using the peek
     *         animation, as an instance of the class {@link TabItem}. The tab item may not be null
     * @return The listener, which has been created, as an instance of the type {@link
     * AnimatorListener}. The listener may not be null
     */
    @NonNull
    private AnimatorListener createRevertPeekAnimationListener(@NonNull final TabItem tabItem) {
        return new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(final Animator animation) {
                super.onAnimationEnd(animation);
                tabViewRecycler.remove(tabItem);
            }

        };
    }

    /**
     * Creates and returns an animation listener, which allows to zoom in the currently selected
     * tab, when a peek animation has been ended.
     *
     * @param selectedTabItem
     *         The tab item, which corresponds to the currently selected tab, as an instance of the
     *         class {@link TabItem}. The tab item may not be null
     * @param peekAnimation
     *         The peek animation as an instance of the class {@link PeekAnimation}. The peek
     *         animation may not be null
     * @return The listener, which has been created, as an instance of the type {@link
     * AnimatorListener}. The listener may not be null
     */
    @NonNull
    private AnimatorListener createZoomOutAnimationListener(@NonNull final TabItem selectedTabItem,
                                                            @NonNull final PeekAnimation peekAnimation) {
        return new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(final Animator animation) {
                super.onAnimationEnd(animation);
                getModel().removeListener(PhoneTabSwitcherLayout.this);
                getModel().hideSwitcher();
                long totalDuration =
                        peekAnimation.getDuration() != -1 ? peekAnimation.getDuration() :
                                peekAnimationDuration;
                long duration = totalDuration / 3;
                Interpolator interpolator =
                        peekAnimation.getInterpolator() != null ? peekAnimation.getInterpolator() :
                                new AccelerateDecelerateInterpolator();
                animateHideSwitcher(selectedTabItem, duration, interpolator, duration,
                        createZoomInAnimationListener(selectedTabItem));
            }

        };
    }

    /**
     * Creates and returns an animation listener, which allows to restore the original state of a
     * tab, when an animation, which zooms in the tab, has been ended.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, which has been zoomed in, as an instance
     *         of the class {@link TabItem}. The tab item may not be null
     * @return The listener, which has been created, as an instance of the type {@link
     * AnimatorListener}. The listener may not be null
     */
    private AnimatorListener createZoomInAnimationListener(@NonNull final TabItem tabItem) {
        return new AnimatorListenerAdapter() {

            @Override
            public void onAnimationEnd(final Animator animation) {
                super.onAnimationEnd(animation);
                getModel().addListener(PhoneTabSwitcherLayout.this);
                tabViewRecycler.inflate(tabItem);
                tabViewRecycler.clearCache();
                recyclerAdapter.clearCachedPreviews();
                tabViewBottomMargin = -1;
            }

        };
    }

    /**
     * Adapts the stack, which is located at the start, when swiping a tab.
     *
     * @param swipedTabItem
     *         The tab item, which corresponds to the swiped tab, as an instance of the class {@link
     *         TabItem}. The tab item may not be null
     * @param successorIndex
     *         The index of the tab, which is located after the swiped tab, as an {@link Integer}
     *         value
     * @param count
     *         The number of tabs, which are contained by the tab switcher, excluding the swiped
     *         tab, as an {@link Integer} value
     */
    private void adaptStackOnSwipe(@NonNull final TabItem swipedTabItem, final int successorIndex,
                                   final int count) {
        if (swipedTabItem.getTag().getState() == State.STACKED_START_ATOP &&
                successorIndex < getModel().getCount()) {
            TabItem tabItem = TabItem.create(getTabSwitcher(), tabViewRecycler, successorIndex);
            State state = tabItem.getTag().getState();

            if (state == State.HIDDEN || state == State.STACKED_START) {
                Pair<Float, State> pair =
                        calculatePositionAndStateWhenStackedAtStart(count, swipedTabItem.getIndex(),
                                (State) null);
                tabItem.getTag().setPosition(pair.first);
                tabItem.getTag().setState(pair.second);
                inflateOrRemoveView(tabItem);
            }
        }
    }

    /**
     * Adapts the stack, which located at the start, when swiping a tab has been aborted.
     *
     * @param swipedTabItem
     *         The tab item, which corresponds to the swiped tab, as an instance of the class {@link
     *         TabItem}. The tab item may not be null
     * @param successorIndex
     *         The index of the the tab, which is located after the swiped tab, as an {@link
     *         Integer} value
     */
    private void adaptStackOnSwipeAborted(@NonNull final TabItem swipedTabItem,
                                          final int successorIndex) {
        if (swipedTabItem.getTag().getState() == State.STACKED_START_ATOP &&
                successorIndex < getModel().getCount()) {
            TabItem tabItem = TabItem.create(getTabSwitcher(), tabViewRecycler, successorIndex);

            if (tabItem.getTag().getState() == State.STACKED_START_ATOP) {
                Pair<Float, State> pair =
                        calculatePositionAndStateWhenStackedAtStart(getTabSwitcher().getCount(),
                                tabItem.getIndex(), swipedTabItem);
                tabItem.getTag().setPosition(pair.first);
                tabItem.getTag().setState(pair.second);
                inflateOrRemoveView(tabItem);
            }
        }
    }

    /**
     * Adapts the size of the view, which is used to visualize a specific tab.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, whose view should be adapted, as an
     *         instance of the class {@link TabItem}. The tab item may not be null
     */
    private void adaptViewSize(@NonNull final TabItem tabItem) {
        View view = tabItem.getView();
        getArithmetics().setPivot(Axis.DRAGGING_AXIS, view,
                getArithmetics().getPivot(Axis.DRAGGING_AXIS, view, DragState.NONE));
        getArithmetics().setPivot(Axis.ORTHOGONAL_AXIS, view,
                getArithmetics().getPivot(Axis.ORTHOGONAL_AXIS, view, DragState.NONE));
        float scale = getArithmetics().getScale(view, true);
        getArithmetics().setScale(Axis.DRAGGING_AXIS, view, scale);
        getArithmetics().setScale(Axis.ORTHOGONAL_AXIS, view, scale);
    }

    /**
     * Relocates all previous tabs, when a floating tab has been removed from the tab switcher.
     *
     * @param removedTabItem
     *         The tab item, which corresponds to the tab, which has been removed, as an instance of
     *         the class {@link TabItem}. The tab item may not be null
     * @param attachedPositionChanged
     *         True, if removing the tab caused the attached position to be changed, false
     *         otherwise
     */
    private void relocateWhenRemovingFloatingTab(@NonNull final TabItem removedTabItem,
                                                 final float attachedPosition,
                                                 boolean attachedPositionChanged) {
        AbstractTabItemIterator iterator;
        TabItem tabItem;
        float defaultTabSpacing = calculateMaxTabSpacing(null);
        float minTabSpacing = calculateMinTabSpacing();
        int referenceIndex = removedTabItem.getIndex();
        TabItem currentReferenceTabItem = removedTabItem;
        float referencePosition = removedTabItem.getTag().getPosition();

        if (attachedPositionChanged && getModel().getCount() > 0) {
            int neighboringIndex =
                    removedTabItem.getIndex() > 0 ? referenceIndex - 1 : referenceIndex;
            referencePosition += Math.abs(
                    TabItem.create(getTabSwitcher(), tabViewRecycler, neighboringIndex).getTag()
                            .getPosition() - referencePosition) / 2f;
        }

        referencePosition =
                Math.min(calculateMaxEndPosition(removedTabItem.getIndex() - 1), referencePosition);
        float initialReferencePosition = referencePosition;

        if (removedTabItem.getIndex() > 0) {
            int selectedTabIndex = getModel().getSelectedTabIndex();
            TabItem selectedTabItem =
                    TabItem.create(getTabSwitcher(), tabViewRecycler, selectedTabIndex);
            float maxTabSpacing = calculateMaxTabSpacing(selectedTabItem);
            iterator = new TabItemIterator.Builder(getTabSwitcher(), tabViewRecycler)
                    .start(removedTabItem.getIndex() - 1).reverse(true).create();

            while ((tabItem = iterator.next()) != null) {
                TabItem predecessor = iterator.peek();
                float currentTabSpacing = calculateMaxTabSpacing(currentReferenceTabItem);
                Pair<Float, State> pair;

                if (tabItem.getIndex() == removedTabItem.getIndex() - 1) {
                    pair = clipTabPosition(tabItem.getIndex(), referencePosition, predecessor);
                    currentReferenceTabItem = tabItem;
                    referencePosition = pair.first;
                    referenceIndex = tabItem.getIndex();
                } else if (referencePosition >= attachedPosition - currentTabSpacing) {
                    float position;

                    if (selectedTabIndex > tabItem.getIndex() &&
                            selectedTabIndex <= referenceIndex) {
                        position = referencePosition + maxTabSpacing +
                                ((referenceIndex - tabItem.getIndex() - 1) * defaultTabSpacing);
                    } else {
                        position = referencePosition +
                                ((referenceIndex - tabItem.getIndex()) * defaultTabSpacing);
                    }

                    pair = clipTabPosition(tabItem.getIndex(), position, predecessor);
                } else {
                    TabItem successor = iterator.previous();
                    float successorPosition = successor.getTag().getPosition();
                    float position = (attachedPosition * (successorPosition + minTabSpacing)) /
                            (minTabSpacing + attachedPosition - currentTabSpacing);
                    pair = clipTabPosition(tabItem.getIndex(), position, predecessor);

                    if (pair.first >= attachedPosition - currentTabSpacing) {
                        currentReferenceTabItem = tabItem;
                        referencePosition = pair.first;
                        referenceIndex = tabItem.getIndex();
                    }
                }

                Tag tag = tabItem.getTag().clone();
                tag.setPosition(pair.first);
                tag.setState(pair.second);

                if (tag.getState() != State.HIDDEN) {
                    long startDelay = Math.abs(removedTabItem.getIndex() - tabItem.getIndex()) *
                            relocateAnimationDelay;

                    if (!tabItem.isInflated()) {
                        Pair<Float, State> pair2 =
                                calculatePositionAndStateWhenStackedAtEnd(tabItem.getIndex());
                        tabItem.getTag().setPosition(pair2.first);
                        tabItem.getTag().setState(pair2.second);
                    }

                    relocate(tabItem, tag.getPosition(), tag, startDelay);
                } else {
                    break;
                }
            }
        }

        if (attachedPositionChanged && getModel().getCount() > 2 &&
                removedTabItem.getTag().getState() != State.STACKED_START_ATOP) {
            iterator = new TabItemIterator.Builder(getTabSwitcher(), tabViewRecycler)
                    .start(removedTabItem.getIndex()).create();
            float previousPosition = initialReferencePosition;
            Tag previousTag = removedTabItem.getTag();

            while ((tabItem = iterator.next()) != null &&
                    tabItem.getIndex() < getModel().getCount() - 1) {
                float position = calculateSuccessorPosition(previousPosition,
                        calculateMaxTabSpacing(tabItem));
                Pair<Float, State> pair =
                        clipTabPosition(tabItem.getIndex(), position, previousTag.getState());
                Tag tag = tabItem.getTag().clone();
                tag.setPosition(pair.first);
                tag.setState(pair.second);
                long startDelay = (Math.abs(removedTabItem.getIndex() - tabItem.getIndex()) + 1) *
                        relocateAnimationDelay;

                if (!tabItem.isInflated()) {
                    Pair<Float, State> pair2 =
                            calculatePositionAndStateWhenStackedAtStart(getModel().getCount(),
                                    tabItem.getIndex(), iterator.previous());
                    tabItem.getTag().setPosition(pair2.first);
                    tabItem.getTag().setState(pair2.second);
                }

                relocate(tabItem, tag.getPosition(), tag, startDelay);
                previousPosition = pair.first;
                previousTag = tag;

                if (pair.second == State.HIDDEN || pair.second == State.STACKED_START) {
                    break;
                }
            }
        }
    }

    /**
     * Relocates all neighboring tabs, when a stacked tab has been removed from the tab switcher.
     *
     * @param removedTabItem
     *         The tab item, which corresponds to the tab, which has been removed, as an instance of
     *         the class {@link TabItem}. The tab item may not be null
     * @param start
     *         True, if the removed tab was part of the stack, which is located at the start, false,
     *         if it was part of the stack, which is located at the end
     */
    private void relocateWhenRemovingStackedTab(@NonNull final TabItem removedTabItem,
                                                final boolean start) {
        int startIndex = removedTabItem.getIndex() + (start ? -1 : 0);
        TabItemIterator iterator =
                new TabItemIterator.Builder(getTabSwitcher(), tabViewRecycler).reverse(start)
                        .start(startIndex).create();
        TabItem tabItem;
        float previousProjectedPosition = removedTabItem.getTag().getPosition();

        while ((tabItem = iterator.next()) != null &&
                (tabItem.getTag().getState() == State.HIDDEN ||
                        tabItem.getTag().getState() == State.STACKED_START ||
                        tabItem.getTag().getState() == State.STACKED_START_ATOP ||
                        tabItem.getTag().getState() == State.STACKED_END)) {
            float projectedPosition = tabItem.getTag().getPosition();

            if (tabItem.getTag().getState() == State.HIDDEN) {
                TabItem previous = iterator.previous();
                tabItem.getTag().setState(previous.getTag().getState());

                if (tabItem.isVisible()) {
                    Pair<Float, State> pair = start ?
                            calculatePositionAndStateWhenStackedAtStart(getTabSwitcher().getCount(),
                                    tabItem.getIndex(), tabItem) :
                            calculatePositionAndStateWhenStackedAtEnd(tabItem.getIndex());
                    tabItem.getTag().setPosition(pair.first);
                    tabItem.getTag().setState(pair.second);
                    inflateAndUpdateView(tabItem, null);
                }

                break;
            } else {
                tabItem.getTag().setPosition(previousProjectedPosition);
                long startDelay =
                        (Math.abs(startIndex - tabItem.getIndex()) + 1) * relocateAnimationDelay;
                animateRelocate(tabItem, previousProjectedPosition, null, startDelay,
                        createRelocateAnimationListener(tabItem));
            }

            previousProjectedPosition = projectedPosition;
        }
    }

    /**
     * Relocates all previous tabs, when floating tabs have been added to the tab switcher.
     *
     * @param addedTabItems
     *         An array, which contains the tab items, which correspond to the tabs, which have been
     *         added, as an array of the type {@link TabItem}. The array may not be null
     * @param referenceTabItem
     *         The tab item, which corresponds to the tab, which is used as a reference, as an
     *         instance of the class {@link TabItem}. The tab item may not be null
     * @param isReferencingPredecessor
     *         True, if the tab, which is used as a reference, is the predecessor of the added tab,
     *         false if it is the successor
     * @param attachedPosition
     *         The current attached position in pixels as a {@link Float} value
     * @param attachedPositionChanged
     *         True, if adding the tab caused the attached position to be changed, false otherwise
     * @return An array, which contains the tab items, which correspond to the tabs, which have been
     * added, as an array of the type {@link TabItem}. The array may not be null
     */
    @NonNull
    private TabItem[] relocateWhenAddingFloatingTabs(@NonNull final TabItem[] addedTabItems,
                                                     @NonNull final TabItem referenceTabItem,
                                                     final boolean isReferencingPredecessor,
                                                     final float attachedPosition,
                                                     final boolean attachedPositionChanged) {
        TabItem firstAddedTabItem = addedTabItems[0];
        TabItem lastAddedTabItem = addedTabItems[addedTabItems.length - 1];
        float referencePosition = referenceTabItem.getTag().getPosition();

        if (isReferencingPredecessor && attachedPositionChanged &&
                lastAddedTabItem.getIndex() < getModel().getCount() - 1) {
            int neighboringIndex = lastAddedTabItem.getIndex() + 1;
            referencePosition -= Math.abs(referencePosition -
                    TabItem.create(getTabSwitcher(), tabViewRecycler, neighboringIndex).getTag()
                            .getPosition()) / 2f;
        }

        float initialReferencePosition = referencePosition;
        int selectedTabIndex = getModel().getSelectedTabIndex();
        TabItem selectedTabItem =
                TabItem.create(getTabSwitcher(), tabViewRecycler, selectedTabIndex);
        float defaultTabSpacing = calculateMaxTabSpacing(null);
        float maxTabSpacing = calculateMaxTabSpacing(selectedTabItem);
        float minTabSpacing = calculateMinTabSpacing();
        TabItem currentReferenceTabItem = referenceTabItem;
        int referenceIndex = referenceTabItem.getIndex();
        AbstractTabItemIterator.AbstractBuilder builder =
                new TabItemIterator.Builder(getTabSwitcher(), tabViewRecycler);

        for (TabItem addedTabItem : addedTabItems) {
            int iterationReferenceIndex = referenceIndex;
            float iterationReferencePosition = referencePosition;
            TabItem iterationReferenceTabItem = currentReferenceTabItem;
            AbstractTabItemIterator iterator =
                    builder.start(addedTabItem.getIndex()).reverse(true).create();
            TabItem tabItem;

            while ((tabItem = iterator.next()) != null) {
                TabItem predecessor = iterator.peek();
                Pair<Float, State> pair;
                float currentTabSpacing = calculateMaxTabSpacing(iterationReferenceTabItem);

                if (isReferencingPredecessor && tabItem.getIndex() == addedTabItem.getIndex()) {
                    State predecessorState =
                            predecessor != null ? predecessor.getTag().getState() : null;
                    pair = clipTabPosition(tabItem.getIndex(), iterationReferencePosition,
                            predecessorState == State.STACKED_START_ATOP ? State.FLOATING :
                                    predecessorState);
                    currentReferenceTabItem = iterationReferenceTabItem = tabItem;
                    initialReferencePosition =
                            referencePosition = iterationReferencePosition = pair.first;
                    referenceIndex = iterationReferenceIndex = tabItem.getIndex();
                } else if (iterationReferencePosition >= attachedPosition - currentTabSpacing) {
                    float position;

                    if (selectedTabIndex > tabItem.getIndex() &&
                            selectedTabIndex <= iterationReferenceIndex) {
                        position = iterationReferencePosition + maxTabSpacing +
                                ((iterationReferenceIndex - tabItem.getIndex() - 1) *
                                        defaultTabSpacing);
                    } else {
                        position = iterationReferencePosition +
                                ((iterationReferenceIndex - tabItem.getIndex()) *
                                        defaultTabSpacing);
                    }

                    pair = clipTabPosition(tabItem.getIndex(), position, predecessor);
                } else {
                    TabItem successor = iterator.previous();
                    float successorPosition = successor.getTag().getPosition();
                    float position = (attachedPosition * (successorPosition + minTabSpacing)) /
                            (minTabSpacing + attachedPosition - currentTabSpacing);
                    pair = clipTabPosition(tabItem.getIndex(), position, predecessor);

                    if (pair.first >= attachedPosition - currentTabSpacing) {
                        iterationReferenceTabItem = tabItem;
                        iterationReferencePosition = pair.first;
                        iterationReferenceIndex = tabItem.getIndex();
                    }
                }

                if (tabItem.getIndex() >= firstAddedTabItem.getIndex() &&
                        tabItem.getIndex() <= lastAddedTabItem.getIndex()) {
                    if (!isReferencingPredecessor && attachedPositionChanged &&
                            getModel().getCount() > 3) {
                        TabItem successor = iterator.previous();
                        float successorPosition = successor.getTag().getPosition();
                        float position = pair.first - Math.abs(pair.first - successorPosition) / 2f;
                        pair = clipTabPosition(tabItem.getIndex(), position, predecessor);
                        initialReferencePosition = pair.first;
                    }

                    Tag tag = addedTabItems[tabItem.getIndex() - firstAddedTabItem.getIndex()]
                            .getTag();
                    tag.setPosition(pair.first);
                    tag.setState(pair.second);
                } else {
                    Tag tag = tabItem.getTag().clone();
                    tag.setPosition(pair.first);
                    tag.setState(pair.second);

                    if (!tabItem.isInflated()) {
                        Pair<Float, State> pair2 =
                                calculatePositionAndStateWhenStackedAtEnd(tabItem.getIndex());
                        tabItem.getTag().setPosition(pair2.first);
                        tabItem.getTag().setState(pair2.second);
                    }

                    relocate(tabItem, tag.getPosition(), tag, 0);
                }

                if (pair.second == State.HIDDEN || pair.second == State.STACKED_END) {
                    setFirstVisibleIndex(getFirstVisibleIndex() + 1);
                    break;
                }
            }
        }

        if (attachedPositionChanged && getModel().getCount() > 3) {
            AbstractTabItemIterator iterator =
                    builder.start(lastAddedTabItem.getIndex() + 1).reverse(false).create();
            TabItem tabItem;
            float previousPosition = initialReferencePosition;
            Tag previousTag = lastAddedTabItem.getTag();

            while ((tabItem = iterator.next()) != null &&
                    tabItem.getIndex() < getModel().getCount() - 1) {
                float position = calculateSuccessorPosition(previousPosition,
                        calculateMaxTabSpacing(tabItem));
                Pair<Float, State> pair =
                        clipTabPosition(tabItem.getIndex(), position, previousTag.getState());
                Tag tag = tabItem.getTag().clone();
                tag.setPosition(pair.first);
                tag.setState(pair.second);

                if (!tabItem.isInflated()) {
                    Pair<Float, State> pair2 =
                            calculatePositionAndStateWhenStackedAtStart(getModel().getCount(),
                                    tabItem.getIndex(), iterator.previous());
                    tabItem.getTag().setPosition(pair2.first);
                    tabItem.getTag().setState(pair2.second);
                }

                relocate(tabItem, tag.getPosition(), tag, 0);
                previousPosition = pair.first;
                previousTag = tag;

                if (pair.second == State.HIDDEN || pair.second == State.STACKED_START) {
                    break;
                }
            }
        }

        return addedTabItems;
    }

    /**
     * Relocates all neighboring tabs, when stacked tabs have been added to the tab switcher.
     *
     * @param start
     *         True, if the added tab was part of the stack, which is located at the start, false,
     *         if it was part of the stack, which is located at the end
     * @param addedTabItems
     *         An array, which contains the tab items, which correspond to the tabs, which have been
     *         added, as an array of the type {@link TabItem}. The array may not be null
     * @return An array, which contains the tab items, which correspond to the tabs, which have been
     * added, as an array of the type {@link TabItem}. The array may not be null
     */
    @NonNull
    private TabItem[] relocateWhenAddingStackedTabs(final boolean start,
                                                    @NonNull final TabItem[] addedTabItems) {
        if (!start) {
            setFirstVisibleIndex(getFirstVisibleIndex() + addedTabItems.length);
        }

        TabItem firstAddedTabItem = addedTabItems[0];
        TabItem lastAddedTabItem = addedTabItems[addedTabItems.length - 1];
        AbstractTabItemIterator iterator =
                new TabItemIterator.Builder(getTabSwitcher(), tabViewRecycler)
                        .start(start ? lastAddedTabItem.getIndex() : firstAddedTabItem.getIndex())
                        .reverse(start).create();
        TabItem tabItem;

        while ((tabItem = iterator.next()) != null &&
                (tabItem.getTag().getState() == State.STACKED_START ||
                        tabItem.getTag().getState() == State.STACKED_START_ATOP ||
                        tabItem.getTag().getState() == State.STACKED_END ||
                        tabItem.getTag().getState() == State.HIDDEN)) {
            TabItem predecessor = start ? iterator.peek() : iterator.previous();
            Pair<Float, State> pair = start ?
                    calculatePositionAndStateWhenStackedAtStart(getModel().getCount(),
                            tabItem.getIndex(), predecessor) :
                    calculatePositionAndStateWhenStackedAtEnd(tabItem.getIndex());

            if (start && predecessor != null && predecessor.getTag().getState() == State.FLOATING) {
                float predecessorPosition = predecessor.getTag().getPosition();
                float distance = predecessorPosition - pair.first;

                if (distance > calculateMinTabSpacing()) {
                    float position = calculateSuccessorPosition(tabItem, predecessor);
                    pair = clipTabPosition(tabItem.getIndex(), position, predecessor);
                }
            }

            if (tabItem.getIndex() >= firstAddedTabItem.getIndex() &&
                    tabItem.getIndex() <= lastAddedTabItem.getIndex()) {
                Tag tag = addedTabItems[tabItem.getIndex() - firstAddedTabItem.getIndex()].getTag();
                tag.setPosition(pair.first);
                tag.setState(pair.second);
            } else if (tabItem.isInflated()) {
                Tag tag = tabItem.getTag().clone();
                tag.setPosition(pair.first);
                tag.setState(pair.second);
                animateRelocate(tabItem, tag.getPosition(), tag, 0,
                        createRelocateAnimationListener(tabItem));
            } else {
                break;
            }
        }

        return addedTabItems;
    }

    /**
     * Calculates the position and state of hidden tabs, which have been added to the tab switcher.
     *
     * @param addedTabItems
     *         An array, which contains the tab items, which correspond to the tabs, which have been
     *         added, as an array of the type {@link TabItem}. The array may not be null
     * @param referenceTabItem
     *         The tab item, which corresponds to the tab, which is used as a reference, as an
     *         instance of the class {@link TabItem}. The tab item may not be null
     * @return An array, which contains the tab items, which correspond to the tabs, which have been
     * added, as an array of the type {@link TabItem}. The array may not be null
     */
    @NonNull
    private TabItem[] relocateWhenAddingHiddenTabs(@NonNull final TabItem[] addedTabItems,
                                                   @NonNull final TabItem referenceTabItem) {
        boolean stackedAtStart = isStackedAtStart(referenceTabItem.getIndex());

        for (TabItem tabItem : addedTabItems) {
            Pair<Float, State> pair;

            if (stackedAtStart) {
                TabItem predecessor = tabItem.getIndex() > 0 ?
                        TabItem.create(getTabSwitcher(), tabViewRecycler, tabItem.getIndex() - 1) :
                        null;
                pair = calculatePositionAndStateWhenStackedAtStart(getModel().getCount(),
                        tabItem.getIndex(), predecessor);
            } else {
                pair = calculatePositionAndStateWhenStackedAtEnd(tabItem.getIndex());
            }

            Tag tag = tabItem.getTag();
            tag.setPosition(pair.first);
            tag.setState(pair.second);
        }

        return addedTabItems;
    }

    /**
     * Relocates a specific tab. If its view is now yet inflated, it is inflated first.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, which should be relocated, as an instance
     *         of the class {@link TabItem}. The tab item may not be null
     * @param relocatePosition
     *         The position, the tab should be moved to, in pixels as an {@link Float} value
     * @param tag
     *         The tag, which should be applied to the tab, once it has been relocated, as an
     *         instance of the class {@link Tag} or null, if no tag should be applied
     * @param startDelay
     *         The start delay of the relocate animation in milliseconds as a {@link Long} value
     */
    private void relocate(@NonNull final TabItem tabItem, final float relocatePosition,
                          @Nullable final Tag tag, final long startDelay) {
        if (tabItem.isInflated()) {
            animateRelocate(tabItem, relocatePosition, tag, startDelay,
                    createRelocateAnimationListener(tabItem));
        } else {
            inflateAndUpdateView(tabItem,
                    createRelocateLayoutListener(tabItem, relocatePosition, tag, startDelay,
                            createRelocateAnimationListener(tabItem)));
            tabItem.getView().setVisibility(View.INVISIBLE);
        }
    }

    /**
     * Swipes a specific tab.
     *
     * @param tabItem
     *         The tab item, which corresponds to the tab, which should be swiped, as an instance of
     *         the class {@link TabItem}. The tab item may not be null
     * @param distance
     *         The distance, the tab should be swiped by, in pixels as a {@link Float} value
     */
    private void swipe(@NonNull final TabItem tabItem, final float distance) {
        View view = tabItem.getView();

        if (!tabItem.getTag().isClosing()) {
            adaptStackOnSwipe(tabItem, tabItem.getIndex() + 1, getModel().getCount() - 1);
        }

        tabItem.getTag().setClosing(true);
        float dragDistance = distance;

        if (!tabItem.getTab().isCloseable()) {
            dragDistance = (float) Math.pow(Math.abs(distance), 0.75);
            dragDistance = distance < 0 ? dragDistance * -1 : dragDistance;
        }

        getArithmetics().setPivot(Axis.DRAGGING_AXIS, view,
                getArithmetics().getPivot(Axis.DRAGGING_AXIS, view, DragState.SWIPE));
        getArithmetics().setPivot(Axis.ORTHOGONAL_AXIS, view,
                getArithmetics().getPivot(Axis.ORTHOGONAL_AXIS, view, DragState.SWIPE));
        float scale = getArithmetics().getScale(view, true);
        float ratio = 1 - (Math.abs(dragDistance) / calculateSwipePosition());
        float scaledClosedTabScale = swipedTabScale * scale;
        float targetScale = scaledClosedTabScale + ratio * (scale - scaledClosedTabScale);
        getArithmetics().setScale(Axis.DRAGGING_AXIS, view, targetScale);
        getArithmetics().setScale(Axis.ORTHOGONAL_AXIS, view, targetScale);
        view.setAlpha(swipedTabAlpha + ratio * (1 - swipedTabAlpha));
        getArithmetics().setPosition(Axis.ORTHOGONAL_AXIS, view, dragDistance);
    }

    /**
     * Moves the first tab to overlap the other tabs, when overshooting at the start.
     *
     * @param position
     *         The position of the first tab in pixels as a {@link Float} value
     */
    private void startOvershoot(final float position) {
        TabItemIterator iterator =
                new TabItemIterator.Builder(getTabSwitcher(), tabViewRecycler).create();
        TabItem tabItem;

        while ((tabItem = iterator.next()) != null) {
            if (tabItem.getIndex() == 0) {
                View view = tabItem.getView();
                getArithmetics().setPivot(Axis.DRAGGING_AXIS, view,
                        getArithmetics().getPivot(Axis.DRAGGING_AXIS, view, DragState.NONE));
                getArithmetics().setPivot(Axis.ORTHOGONAL_AXIS, view,
                        getArithmetics().getPivot(Axis.ORTHOGONAL_AXIS, view, DragState.NONE));
                getArithmetics().setPosition(Axis.DRAGGING_AXIS, view, position);
            } else if (tabItem.isInflated()) {
                View firstView = iterator.first().getView();
                View view = tabItem.getView();
                view.setVisibility(getArithmetics().getPosition(Axis.DRAGGING_AXIS, firstView) <=
                        getArithmetics().getPosition(Axis.DRAGGING_AXIS, view) ? View.INVISIBLE :
                        View.VISIBLE);
            }
        }
    }

    /**
     * Tilts the tabs, when overshooting at the start.
     *
     * @param angle
     *         The angle, the tabs should be rotated by, in degrees as a {@link Float} value
     */
    private void tiltOnStartOvershoot(final float angle) {
        TabItemIterator iterator =
                new TabItemIterator.Builder(getTabSwitcher(), tabViewRecycler).create();
        TabItem tabItem;

        while ((tabItem = iterator.next()) != null) {
            View view = tabItem.getView();

            if (tabItem.getIndex() == 0) {
                view.setCameraDistance(maxCameraDistance);
                getArithmetics().setPivot(Axis.DRAGGING_AXIS, view, getArithmetics()
                        .getPivot(Axis.DRAGGING_AXIS, view, DragState.OVERSHOOT_START));
                getArithmetics().setPivot(Axis.ORTHOGONAL_AXIS, view, getArithmetics()
                        .getPivot(Axis.ORTHOGONAL_AXIS, view, DragState.OVERSHOOT_START));
                getArithmetics().setRotation(Axis.ORTHOGONAL_AXIS, view, angle);
            } else if (tabItem.isInflated()) {
                tabItem.getView().setVisibility(View.INVISIBLE);
            }
        }
    }

    /**
     * Tilts the tabs, when overshooting at the end.
     *
     * @param angle
     *         The angle, the tabs should be rotated by, in degrees as a {@link Float} value
     */
    private void tiltOnEndOvershoot(final float angle) {
        float minCameraDistance = maxCameraDistance / 2f;
        int firstVisibleIndex = -1;
        TabItemIterator iterator =
                new TabItemIterator.Builder(getTabSwitcher(), tabViewRecycler).create();
        TabItem tabItem;

        while ((tabItem = iterator.next()) != null) {
            if (tabItem.isInflated()) {
                View view = tabItem.getView();

                if (!iterator.hasNext()) {
                    view.setCameraDistance(maxCameraDistance);
                } else if (firstVisibleIndex == -1) {
                    view.setCameraDistance(minCameraDistance);

                    if (tabItem.getTag().getState() == State.FLOATING) {
                        firstVisibleIndex = tabItem.getIndex();
                    }
                } else {
                    int diff = tabItem.getIndex() - firstVisibleIndex;
                    float ratio =
                            (float) diff / (float) (getModel().getCount() - firstVisibleIndex);
                    view.setCameraDistance(
                            minCameraDistance + (maxCameraDistance - minCameraDistance) * ratio);
                }

                getArithmetics().setPivot(Axis.DRAGGING_AXIS, view, getArithmetics()
                        .getPivot(Axis.DRAGGING_AXIS, view, DragState.OVERSHOOT_END));
                getArithmetics().setPivot(Axis.ORTHOGONAL_AXIS, view, getArithmetics()
                        .getPivot(Axis.ORTHOGONAL_AXIS, view, DragState.OVERSHOOT_END));
                getArithmetics().setRotation(Axis.ORTHOGONAL_AXIS, view, angle);
            }
        }
    }

    /**
     * Returns, whether a hidden tab at a specific index, is part of the stack, which is located at
     * the start, or not.
     *
     * @param index
     *         The index of the hidden tab, as an {@link Integer} value
     * @return True, if the hidden tab is part of the stack, which is located at the start, false
     * otherwise
     */
    private boolean isStackedAtStart(final int index) {
        boolean start = true;
        AbstractTabItemIterator iterator =
                new TabItemIterator.Builder(getTabSwitcher(), tabViewRecycler).start(index + 1)
                        .create();
        TabItem tabItem;

        while ((tabItem = iterator.next()) != null) {
            State state = tabItem.getTag().getState();

            if (state == State.STACKED_START) {
                start = true;
                break;
            } else if (state == State.FLOATING) {
                start = false;
                break;
            }
        }

        return start;
    }

    /**
     * Creates a new layout, which implements the functionality of a {@link TabSwitcher} on
     * smartphones.
     *
     * @param tabSwitcher
     *         The tab switcher, the layout belongs to, as an instance of the class {@link
     *         TabSwitcher}. The tab switcher may not be null
     * @param model
     *         The model of the tab switcher, the layout belongs to, as an instance of the class
     *         {@link TabSwitcherModel}. The model may not be null
     * @param arithmetics
     *         The arithmetics, which should be used by the layout, as an instance of the class
     *         {@link PhoneArithmetics}. The arithmetics may not be null
     * @param themeHelper
     *         The theme helper, which allows to retrieve resources, depending on the tab switcher's
     *         theme, as an instance of the class {@link ThemeHelper}. The theme helper may not be
     *         null
     */
    public PhoneTabSwitcherLayout(@NonNull final TabSwitcher tabSwitcher,
                                  @NonNull final TabSwitcherModel model,
                                  @NonNull final PhoneArithmetics arithmetics,
                                  @NonNull final ThemeHelper themeHelper) {
        super(tabSwitcher, model, arithmetics, themeHelper);
        Resources resources = tabSwitcher.getResources();
        stackedTabCount = resources.getInteger(R.integer.phone_stacked_tab_count);
        tabInset = resources.getDimensionPixelSize(R.dimen.tab_inset);
        tabBorderWidth = resources.getDimensionPixelSize(R.dimen.tab_border_width);
        tabTitleContainerHeight =
                resources.getDimensionPixelSize(R.dimen.tab_title_container_height);
        maxCameraDistance = resources.getDimensionPixelSize(R.dimen.max_camera_distance);
        TypedValue typedValue = new TypedValue();
        resources.getValue(R.dimen.swiped_tab_scale, typedValue, true);
        swipedTabScale = typedValue.getFloat();
        resources.getValue(R.dimen.swiped_tab_alpha, typedValue, true);
        swipedTabAlpha = typedValue.getFloat();
        showSwitcherAnimationDuration =
                resources.getInteger(R.integer.show_switcher_animation_duration);
        hideSwitcherAnimationDuration =
                resources.getInteger(R.integer.hide_switcher_animation_duration);
        toolbarVisibilityAnimationDuration =
                resources.getInteger(R.integer.toolbar_visibility_animation_duration);
        toolbarVisibilityAnimationDelay =
                resources.getInteger(R.integer.toolbar_visibility_animation_delay);
        swipeAnimationDuration = resources.getInteger(R.integer.swipe_animation_duration);
        clearAnimationDelay = resources.getInteger(R.integer.clear_animation_delay);
        relocateAnimationDuration = resources.getInteger(R.integer.relocate_animation_duration);
        relocateAnimationDelay = resources.getInteger(R.integer.relocate_animation_delay);
        revertOvershootAnimationDuration =
                resources.getInteger(R.integer.revert_overshoot_animation_duration);
        revealAnimationDuration = resources.getInteger(R.integer.reveal_animation_duration);
        peekAnimationDuration = resources.getInteger(R.integer.peek_animation_duration);
        maxStartOvershootAngle = resources.getInteger(R.integer.max_start_overshoot_angle);
        maxEndOvershootAngle = resources.getInteger(R.integer.max_end_overshoot_angle);
        tabViewBottomMargin = -1;
        toolbarAnimation = null;
    }

    @Override
    protected final void onInflateLayout(final boolean tabsOnly) {
        LayoutInflater inflater = LayoutInflater.from(getContext());

        if (tabsOnly) {
            toolbar = (Toolbar) getTabSwitcher().findViewById(R.id.primary_toolbar);
            tabContainer = (ViewGroup) getTabSwitcher().findViewById(R.id.tab_container);
        } else {
            toolbar = (Toolbar) inflater.inflate(R.layout.phone_toolbar, getTabSwitcher(), false);
            toolbar.setVisibility(getModel().areToolbarsShown() ? View.VISIBLE : View.INVISIBLE);
            getTabSwitcher().addView(toolbar);
            tabContainer = new FrameLayout(getContext());
            tabContainer.setId(R.id.tab_container);
            getTabSwitcher().addView(tabContainer, FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT);
        }

        contentViewRecycler = new ViewRecycler<>(inflater);
        recyclerAdapter = new PhoneRecyclerAdapter(getTabSwitcher(), getModel(), getThemeHelper(),
                contentViewRecycler);
        getModel().addListener(recyclerAdapter);
        tabViewRecycler = new AttachedViewRecycler<>(tabContainer, inflater,
                Collections.reverseOrder(new TabItem.Comparator(getTabSwitcher())));
        tabViewRecycler.setAdapter(recyclerAdapter);
        recyclerAdapter.setViewRecycler(tabViewRecycler);
        dragHandler = new PhoneDragHandler(getTabSwitcher(), getArithmetics(), tabViewRecycler);
        adaptDecorator();
        adaptToolbarMargin();
    }

    @Override
    protected final void onDetachLayout(final boolean tabsOnly) {
        contentViewRecycler.removeAll();
        contentViewRecycler.clearCache();
        recyclerAdapter.clearCachedPreviews();

        if (!tabsOnly) {
            getModel().removeListener(recyclerAdapter);
        }
    }

    @Override
    protected final AbstractDragHandler<?> getDragHandler() {
        return dragHandler;
    }

    @Override
    protected final ViewRecycler<Tab, Void> getContentViewRecycler() {
        return contentViewRecycler;
    }

    @Override
    protected final AttachedViewRecycler<TabItem, Integer> getTabViewRecycler() {
        return tabViewRecycler;
    }

    @Override
    protected final void updateView(@NonNull final TabItem tabItem) {
        View view = tabItem.getView();
        view.setAlpha(1f);
        view.setVisibility(View.VISIBLE);
        getArithmetics().setPivot(Arithmetics.Axis.DRAGGING_AXIS, view, getArithmetics()
                .getPivot(Arithmetics.Axis.DRAGGING_AXIS, view,
                        AbstractDragHandler.DragState.NONE));
        getArithmetics().setPivot(Arithmetics.Axis.ORTHOGONAL_AXIS, view, getArithmetics()
                .getPivot(Arithmetics.Axis.ORTHOGONAL_AXIS, view,
                        AbstractDragHandler.DragState.NONE));
        super.updateView(tabItem);
        getArithmetics().setRotation(Arithmetics.Axis.ORTHOGONAL_AXIS, view, 0);
    }

    @Override
    protected final float calculateAttachedPosition(final int count) {
        Toolbar[] toolbars = getTabSwitcher().getToolbars();
        float totalSpace = getArithmetics().getSize(Axis.DRAGGING_AXIS, getTabSwitcher()) -
                (getTabSwitcher().getLayout() == Layout.PHONE_PORTRAIT &&
                        getTabSwitcher().areToolbarsShown() && toolbars != null ?
                        toolbars[0].getHeight() + tabInset : 0);
        float attachedPosition;

        if (count == 3) {
            attachedPosition = totalSpace * 0.66f;
        } else if (count == 4) {
            attachedPosition = totalSpace * 0.6f;
        } else {
            attachedPosition = totalSpace * 0.5f;
        }

        return attachedPosition;
    }

    @Override
    protected final void inflateAndUpdateView(@NonNull final TabItem tabItem,
                                              @Nullable final OnGlobalLayoutListener listener) {
        inflateView(tabItem, createInflateViewLayoutListener(tabItem, listener),
                tabViewBottomMargin);
    }

    @Override
    protected final int getStackedTabCount() {
        return stackedTabCount;
    }

    @NonNull
    @Override
    protected final Pair<Float, State> calculatePositionAndStateWhenStackedAtStart(final int count,
                                                                                   final int index,
                                                                                   @Nullable final State predecessorState) {
        if ((count - index) <= getStackedTabCount()) {
            float position = getStackedTabSpacing() * (count - (index + 1));
            return Pair.create(position,
                    (predecessorState == null || predecessorState == State.FLOATING) ?
                            State.STACKED_START_ATOP : State.STACKED_START);
        } else {
            float position = getStackedTabSpacing() * getStackedTabCount();
            return Pair.create(position,
                    (predecessorState == null || predecessorState == State.FLOATING) ?
                            State.STACKED_START_ATOP : State.HIDDEN);
        }
    }

    @NonNull
    @Override
    protected final Pair<Float, State> calculatePositionAndStateWhenStackedAtEnd(final int index) {
        float size = getArithmetics().getSize(Arithmetics.Axis.DRAGGING_AXIS, getTabSwitcher());
        Toolbar[] toolbars = getTabSwitcher().getToolbars();
        int toolbarHeight = getTabSwitcher().getLayout() != Layout.PHONE_LANDSCAPE &&
                getTabSwitcher().areToolbarsShown() && toolbars != null ?
                toolbars[0].getHeight() - tabInset : 0;
        int padding = getArithmetics()
                .getPadding(Arithmetics.Axis.DRAGGING_AXIS, Gravity.START, getTabSwitcher()) +
                getArithmetics()
                        .getPadding(Arithmetics.Axis.DRAGGING_AXIS, Gravity.END, getTabSwitcher());
        int offset = getTabSwitcher().getLayout() == Layout.PHONE_LANDSCAPE ?
                getStackedTabCount() * getStackedTabSpacing() : 0;

        if (index < getStackedTabCount()) {
            float position =
                    size - toolbarHeight - tabInset - (getStackedTabSpacing() * (index + 1)) -
                            padding + offset;
            return Pair.create(position, State.STACKED_END);
        } else {
            float position = size - toolbarHeight - tabInset -
                    (getStackedTabSpacing() * getStackedTabCount()) - padding + offset;
            return Pair.create(position, State.HIDDEN);
        }
    }

    @Override
    protected final boolean isOvershootingAtStart() {
        if (getTabSwitcher().getCount() <= 1) {
            return true;
        } else {
            AbstractTabItemIterator iterator =
                    new TabItemIterator.Builder(getTabSwitcher(), tabViewRecycler).create();
            TabItem tabItem = iterator.getItem(0);
            return tabItem.getTag().getState() == State.STACKED_START_ATOP;
        }
    }

    @Override
    protected final boolean isOvershootingAtEnd() {
        if (getTabSwitcher().getCount() <= 1) {
            return true;
        } else {
            AbstractTabItemIterator iterator =
                    new TabItemIterator.Builder(getTabSwitcher(), getTabViewRecycler()).create();
            TabItem lastTabItem = iterator.getItem(getTabSwitcher().getCount() - 1);
            TabItem predecessor = iterator.getItem(getTabSwitcher().getCount() - 2);
            return Math.round(predecessor.getTag().getPosition()) >=
                    Math.round(calculateMaxTabSpacing(lastTabItem));
        }
    }

    @Override
    protected final float calculateMaxEndPosition(final int index) {
        float defaultMaxTabSpacing = calculateMaxTabSpacing(null);
        int selectedTabIndex = getTabSwitcher().getSelectedTabIndex();

        if (selectedTabIndex > index) {
            AbstractTabItemIterator iterator =
                    new TabItemIterator.Builder(getTabSwitcher(), tabViewRecycler).create();
            TabItem selectedTabItem = iterator.getItem(selectedTabIndex);
            float selectedTabSpacing = calculateMaxTabSpacing(selectedTabItem);
            return (getTabSwitcher().getCount() - 2 - index) * defaultMaxTabSpacing +
                    selectedTabSpacing;
        }

        return (getTabSwitcher().getCount() - 1 - index) * defaultMaxTabSpacing;
    }

    @Override
    protected final float calculateSuccessorPosition(@NonNull final TabItem tabItem,
                                                     @NonNull final TabItem predecessor) {
        float predecessorPosition = predecessor.getTag().getPosition();
        float maxTabSpacing = calculateMaxTabSpacing(tabItem);
        return calculateSuccessorPosition(predecessorPosition, maxTabSpacing);
    }

    @Override
    protected final float calculatePredecessorPosition(@NonNull final TabItem tabItem,
                                                       @NonNull final TabItem successor) {
        float successorPosition = successor.getTag().getPosition();
        return successorPosition + calculateMaxTabSpacing(successor);
    }

    @Nullable
    @Override
    public final ViewGroup getTabContainer() {
        return tabContainer;
    }

    @Nullable
    @Override
    public final Toolbar[] getToolbars() {
        return toolbar != null ? new Toolbar[]{toolbar} : null;
    }

    @Override
    public final void onDecoratorChanged(@NonNull final TabSwitcherDecorator decorator) {
        adaptDecorator();
        super.onDecoratorChanged(decorator);
    }

    @Override
    public final void onSwitcherShown() {
        getLogger().logInfo(getClass(), "Showed tab switcher");
        animateShowSwitcher();
    }

    @Override
    public final void onSwitcherHidden() {
        getLogger().logInfo(getClass(), "Hid tab switcher");
        animateHideSwitcher();
    }

    @Override
    public final void onSelectionChanged(final int previousIndex, final int index,
                                         @Nullable final Tab selectedTab,
                                         final boolean switcherHidden) {
        getLogger().logInfo(getClass(), "Selected tab at index " + index);

        if (switcherHidden) {
            animateHideSwitcher();
        } else {
            tabViewRecycler
                    .remove(TabItem.create(getTabSwitcher(), tabViewRecycler, previousIndex));
            tabViewRecycler.inflate(TabItem.create(getTabSwitcher(), tabViewRecycler, index));
        }
    }

    @Override
    public final void onTabAdded(final int index, @NonNull final Tab tab,
                                 final int previousSelectedTabIndex, final int selectedTabIndex,
                                 final boolean switcherVisibilityChanged,
                                 @NonNull final Animation animation) {
        getLogger().logInfo(getClass(),
                "Added tab at index " + index + " using a " + animation.getClass().getSimpleName());

        if (animation instanceof PeekAnimation && !getModel().isEmpty()) {
            ensureTrue(switcherVisibilityChanged, animation.getClass().getSimpleName() +
                    " not supported when the tab switcher is shown");
            PeekAnimation peekAnimation = (PeekAnimation) animation;
            TabItem tabItem = new TabItem(0, tab);
            inflateView(tabItem, createPeekLayoutListener(tabItem, peekAnimation));
        } else if (animation instanceof RevealAnimation && switcherVisibilityChanged) {
            TabItem tabItem = new TabItem(0, tab);
            RevealAnimation revealAnimation = (RevealAnimation) animation;
            inflateView(tabItem, createRevealLayoutListener(tabItem, revealAnimation));
        } else {
            addAllTabs(index, new Tab[]{tab}, animation);
        }
    }

    @Override
    public final void onAllTabsAdded(final int index, @NonNull final Tab[] tabs,
                                     final int previousSelectedTabIndex, final int selectedTabIndex,
                                     @NonNull final Animation animation) {
        ensureTrue(animation instanceof SwipeAnimation,
                animation.getClass().getSimpleName() + " not supported for adding multiple tabs");
        getLogger().logInfo(getClass(),
                "Added " + tabs.length + " tabs at index " + index + " using a " +
                        animation.getClass().getSimpleName());
        addAllTabs(index, tabs, animation);
    }

    @Override
    public final void onTabRemoved(final int index, @NonNull final Tab tab,
                                   final int previousSelectedTabIndex, final int selectedTabIndex,
                                   @NonNull final Animation animation) {
        ensureTrue(animation instanceof SwipeAnimation,
                animation.getClass().getSimpleName() + " not supported for removing tabs");
        getLogger().logInfo(getClass(), "Removed tab at index " + index + " using a " +
                animation.getClass().getSimpleName());
        TabItem removedTabItem = TabItem.create(tabViewRecycler, index, tab);

        if (!getModel().isSwitcherShown()) {
            tabViewRecycler.remove(removedTabItem);

            if (getModel().isEmpty()) {
                toolbar.setAlpha(getModel().areToolbarsShown() ? 1 : 0);
            } else if (selectedTabIndex != previousSelectedTabIndex) {
                tabViewRecycler.inflate(
                        TabItem.create(getTabSwitcher(), tabViewRecycler, selectedTabIndex));
            }
        } else {
            adaptStackOnSwipe(removedTabItem, removedTabItem.getIndex(), getModel().getCount());
            removedTabItem.getTag().setClosing(true);
            SwipeAnimation swipeAnimation =
                    animation instanceof SwipeAnimation ? (SwipeAnimation) animation :
                            new SwipeAnimation.Builder().create();

            if (removedTabItem.isInflated()) {
                animateRemove(removedTabItem, swipeAnimation);
            } else {
                boolean start = isStackedAtStart(index);
                TabItem predecessor = TabItem.create(getTabSwitcher(), tabViewRecycler, index - 1);
                Pair<Float, State> pair = start ?
                        calculatePositionAndStateWhenStackedAtStart(getModel().getCount(), index,
                                predecessor) : calculatePositionAndStateWhenStackedAtEnd(index);
                removedTabItem.getTag().setPosition(pair.first);
                removedTabItem.getTag().setState(pair.second);
                inflateAndUpdateView(removedTabItem,
                        createRemoveLayoutListener(removedTabItem, swipeAnimation));
            }
        }
    }

    @Override
    public final void onAllTabsRemoved(@NonNull final Tab[] tabs,
                                       @NonNull final Animation animation) {
        ensureTrue(animation instanceof SwipeAnimation,
                animation.getClass().getSimpleName() + " not supported for removing tabs ");
        getLogger().logInfo(getClass(),
                "Removed all tabs using a " + animation.getClass().getSimpleName());

        if (!getModel().isSwitcherShown()) {
            tabViewRecycler.removeAll();
            toolbar.setAlpha(getModel().areToolbarsShown() ? 1 : 0);
        } else {
            SwipeAnimation swipeAnimation =
                    animation instanceof SwipeAnimation ? (SwipeAnimation) animation :
                            new SwipeAnimation.Builder().create();
            AbstractTabItemIterator iterator =
                    new ArrayTabItemIterator.Builder(tabViewRecycler, tabs).reverse(true).create();
            TabItem tabItem;
            int startDelay = 0;

            while ((tabItem = iterator.next()) != null) {
                TabItem previous = iterator.previous();

                if (tabItem.getTag().getState() == State.FLOATING ||
                        (previous != null && previous.getTag().getState() == State.FLOATING)) {
                    startDelay += clearAnimationDelay;
                }

                if (tabItem.isInflated()) {
                    animateSwipe(tabItem, true, startDelay, swipeAnimation,
                            !iterator.hasNext() ? createClearAnimationListener() : null);
                }
            }
        }
    }

    @Override
    public final void onPaddingChanged(final int left, final int top, final int right,
                                       final int bottom) {
        // TODO: Detach and re-inflate tabs
        adaptToolbarMargin();
    }

    @Override
    public final void onGlobalLayout() {
        if (getModel().isSwitcherShown()) {
            TabItem[] tabItems = calculateInitialTabItems(getModel().getFirstVisibleTabIndex(),
                    getModel().getFirstVisibleTabPosition());
            AbstractTabItemIterator iterator = new InitialTabItemIterator(tabItems, false, 0);
            TabItem tabItem;

            while ((tabItem = iterator.next()) != null) {
                if (tabItem.isVisible()) {
                    inflateAndUpdateView(tabItem, createBottomMarginLayoutListener(tabItem));
                }
            }

            toolbar.setAlpha(getModel().areToolbarsShown() ? 1 : 0);
        } else if (getModel().getSelectedTab() != null) {
            TabItem tabItem = TabItem.create(getTabSwitcher(), tabViewRecycler,
                    getModel().getSelectedTabIndex());
            tabViewRecycler.inflate(tabItem);
        }
    }

    @Override
    public final void onRevertStartOvershoot() {
        animateRevertStartOvershoot();
        getLogger().logVerbose(getClass(), "Reverting overshoot at the start");
    }

    @Override
    public final void onRevertEndOvershoot() {
        animateRevertEndOvershoot();
        getLogger().logVerbose(getClass(), "Reverting overshoot at the end");
    }

    public final void onStartOvershoot(final float position) {
        startOvershoot(position);
        getLogger().logVerbose(getClass(),
                "Overshooting at the start using a position of " + position + " pixels");
    }

    @Override
    public final void onTiltOnStartOvershoot(final float angle) {
        tiltOnStartOvershoot(angle);
        getLogger().logVerbose(getClass(),
                "Tilting on start overshoot using an angle of " + angle + " degrees");
    }

    @Override
    public final void onTiltOnEndOvershoot(final float angle) {
        tiltOnEndOvershoot(angle);
        getLogger().logVerbose(getClass(),
                "Tilting on end overshoot using an angle of " + angle + " degrees");
    }

    @Override
    public final void onSwipe(@NonNull final TabItem tabItem, final float distance) {
        swipe(tabItem, distance);
        getLogger().logVerbose(getClass(),
                "Swiping tab at index " + tabItem.getIndex() + ". Current swipe distance is " +
                        distance + " pixels");
    }

    @Override
    public final void onSwipeEnded(@NonNull final TabItem tabItem, final boolean remove,
                                   final float velocity) {
        if (remove) {
            View view = tabItem.getView();
            SwipeDirection direction =
                    getArithmetics().getPosition(Axis.ORTHOGONAL_AXIS, view) < 0 ?
                            SwipeDirection.LEFT : SwipeDirection.RIGHT;
            long animationDuration =
                    velocity > 0 ? Math.round((calculateSwipePosition() / velocity) * 1000) : -1;
            Animation animation = new SwipeAnimation.Builder().setDirection(direction)
                    .setDuration(animationDuration).create();
            getModel().removeTab(tabItem.getTab(), animation);
        } else {
            animateSwipe(tabItem, false, 0, new SwipeAnimation.Builder().create(),
                    createSwipeAnimationListener(tabItem));
        }

        getLogger().logVerbose(getClass(),
                "Ended swiping tab at index " + tabItem.getIndex() + ". Tab will " +
                        (remove ? "" : "not ") + "be removed");
    }

}