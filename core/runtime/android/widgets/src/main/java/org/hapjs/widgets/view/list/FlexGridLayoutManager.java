/*
 * Copyright (c) 2021, the hapjs-platform Project Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hapjs.widgets.view.list;

import android.content.Context;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.ViewParent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.FlexRecyclerView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.facebook.yoga.YogaConstants;
import com.facebook.yoga.YogaNode;
import org.hapjs.component.utils.YogaUtil;
import org.hapjs.component.view.YogaLayout;

public class FlexGridLayoutManager extends GridLayoutManager implements FlexLayoutManager {
    public static final int DEFAULT_COLUMN_COUNT = 1;

    private boolean mScrollPage;
    private int mMaxHeight;
    private int mItemCount;
    private int mIndex = Integer.MAX_VALUE;
    private int[] mMeasuredDimension = new int[2];
    private int mHeight;

    private FlexRecyclerView mFlexRecyclerView;
    private RecyclerView.Recycler mRecycler;
    private ViewGroup mParent;

    private int mOverScrolledY;

    public FlexGridLayoutManager(Context context) {
        super(context, DEFAULT_COLUMN_COUNT);
    }

    @Override
    public boolean isAutoMeasureEnabled() {
        return false;
    }

    @Override
    public void onMeasure(
            RecyclerView.Recycler recycler, RecyclerView.State state, int widthSpec,
            int heightSpec) {
        mRecycler = recycler;
        YogaNode node = YogaUtil.getYogaNode(mFlexRecyclerView);

        int maxHeight = 0;
        if (mScrollPage) {
            View moveableView = mFlexRecyclerView.getMoveableView();
            maxHeight =
                    (moveableView == null)
                            ? 0
                            : (moveableView.getMeasuredHeight()
                            - moveableView.getPaddingTop()
                            - moveableView.getPaddingBottom());
        } else {
            if (widthSpec == 0) {
                if (node != null && node.getParent() != null
                        && node.getParent().getChildCount() == 1) {
                    float parentWidth = node.getParent().getLayoutWidth();
                    if (parentWidth > 0) {
                        widthSpec = MeasureSpec
                                .makeMeasureSpec(Math.round(parentWidth), MeasureSpec.EXACTLY);
                    }
                }
            } else {
                int widthMode = MeasureSpec.getMode(widthSpec);
                int widthSize = MeasureSpec.getSize(widthSpec);
                if (widthSize > 0
                        && (widthMode == MeasureSpec.UNSPECIFIED
                        || widthMode == MeasureSpec.AT_MOST)) {
                    widthSpec = MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY);
                }
            }

            if (heightSpec == 0) {
                if (node != null && node.getParent() != null
                        && node.getParent().getChildCount() == 1) {
                    float parentHeight = node.getParent().getLayoutHeight();
                    if (parentHeight > 0) {
                        heightSpec = MeasureSpec
                                .makeMeasureSpec(Math.round(parentHeight), MeasureSpec.EXACTLY);
                    }
                }
            } else {
                int heightMode = MeasureSpec.getMode(heightSpec);
                int heightSize = MeasureSpec.getSize(heightSpec);
                if (heightSize > 0
                        && (heightMode == MeasureSpec.UNSPECIFIED
                        || heightMode == MeasureSpec.AT_MOST)) {
                    heightSpec = MeasureSpec.makeMeasureSpec(heightSize, MeasureSpec.EXACTLY);
                }
            }
        }

        if (maxHeight != mMaxHeight) {
            mIndex = Integer.MAX_VALUE;
            mItemCount = 0;
            mMaxHeight = maxHeight;
        }

        if (!mScrollPage || getOrientation() == HORIZONTAL || maxHeight == 0) {
            super.onMeasure(recycler, state, widthSpec, heightSpec);

            if (node != null && mItemCount != state.getItemCount()) {
                node.dirty();
            }
            mItemCount = state.getItemCount();

            return;
        }

        final int widthSize = View.MeasureSpec.getSize(widthSpec);
        if ((mHeight == maxHeight && state.getItemCount() >= mIndex)
                || state.getItemCount() == mItemCount) {
            setMeasuredDimension(widthSize, mHeight);
            setYogaHeight(mHeight);
            return;
        }

        int height = 0;
        int rowHeight = 0;
        for (int i = 0; i < state.getItemCount(); i++) {
            measureScrapChild(
                    recycler,
                    i,
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    mMeasuredDimension);

            rowHeight = Math.max(rowHeight, mMeasuredDimension[1]);
            if (i % getSpanCount() == getSpanCount() - 1 || i == (state.getItemCount() - 1)) {
                height += rowHeight;
                rowHeight = 0;
            }

            if (height > maxHeight) {
                height = maxHeight;
                mIndex = i;
                break;
            }

            if (i == (state.getItemCount() - 1)) {
                mIndex = i;
            }
        }
        setMeasuredDimension(widthSize, height);
        setYogaHeight(height);
        if (node != null) {
            node.dirty();
            mFlexRecyclerView.setDirty(true);
        }

        mHeight = height;
        mItemCount = state.getItemCount();
    }

    private void measureScrapChild(
            RecyclerView.Recycler recycler,
            int position,
            int widthSpec,
            int heightSpec,
            int[] measuredDimension) {
        View view = recycler.getViewForPosition(position);
        if (view != null) {
            RecyclerView.LayoutParams p = (RecyclerView.LayoutParams) view.getLayoutParams();
            int childWidthSpec =
                    ViewGroup.getChildMeasureSpec(widthSpec, getPaddingLeft() + getPaddingRight(),
                            p.width);
            int childHeightSpec =
                    ViewGroup.getChildMeasureSpec(heightSpec, getPaddingTop() + getPaddingBottom(),
                            p.height);
            view.measure(childWidthSpec, childHeightSpec);
            measuredDimension[0] = view.getMeasuredWidth() + p.leftMargin + p.rightMargin;
            measuredDimension[1] = view.getMeasuredHeight() + p.bottomMargin + p.topMargin;
        }
    }

    private void preMeasureHeight() {
        if (getOrientation() == HORIZONTAL
                || mRecycler == null
                || mFlexRecyclerView == null
                || getStateItemCount() == 0) {
            return;
        }

        if (mParent == null) {
            mParent = (ViewGroup) mFlexRecyclerView.getParent();
        }
        if (mParent == null) {
            return;
        }

        if (mScrollPage) {
            final View moveableView = mFlexRecyclerView.getMoveableView();
            int maxHeight =
                    moveableView.getMeasuredHeight()
                            - moveableView.getPaddingTop()
                            - moveableView.getPaddingBottom();
            if (maxHeight != mMaxHeight) {
                mIndex = Integer.MAX_VALUE;
                mItemCount = 0;
                mMaxHeight = maxHeight;
            }

            int height = 0;
            int rowHeight = 0;
            for (int i = 0; i < getStateItemCount(); i++) {
                measureScrapChild(
                        mRecycler,
                        i,
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        mMeasuredDimension);

                rowHeight = Math.max(rowHeight, mMeasuredDimension[1]);
                if (i % getSpanCount() == getSpanCount() - 1 || i == (getStateItemCount() - 1)) {
                    height += rowHeight;
                    rowHeight = 0;
                }

                if (height > maxHeight) {
                    height = maxHeight;
                    mIndex = i;
                    break;
                }

                if (i == (getStateItemCount() - 1)) {
                    mIndex = i;
                }
            }

            mHeight = height;
            mItemCount = getStateItemCount();

            setYogaHeight(height);
        } else {
            if (mParent instanceof YogaLayout) {
                YogaLayout yogaParent = (YogaLayout) mParent;
                YogaNode recyclerNode = yogaParent.getYogaNodeForView(mFlexRecyclerView);
                recyclerNode.setWidth(YogaConstants.UNDEFINED);
                recyclerNode.setHeight(YogaConstants.UNDEFINED);
            }
        }
    }

    private void setYogaHeight(int height) {
        mParent = (ViewGroup) mFlexRecyclerView.getParent();

        if (mParent instanceof YogaLayout) {
            YogaLayout parentView = (YogaLayout) mParent;
            YogaNode recyclerNode = parentView.getYogaNodeForView(mFlexRecyclerView);
            recyclerNode.setHeight(height);
        }
    }

    @Override
    public int scrollVerticallyBy(int dy, RecyclerView.Recycler recycler,
                                  RecyclerView.State state) {
        int scrolled = super.scrollVerticallyBy(dy, recycler, state);
        int overScrolledY = dy - scrolled;
        if (overScrolledY < 0) {
            mOverScrolledY = overScrolledY;
        } else {
            mOverScrolledY = 0;
        }

        return scrolled;
    }

    @Override
    public void smoothScrollToPosition(
            RecyclerView recyclerView, RecyclerView.State state, int position) {
        final TopSmoothScroller scroller = new TopSmoothScroller(recyclerView.getContext());
        scroller.setTargetPosition(position);
        startSmoothScroll(scroller);
    }

    @Override
    public FlexRecyclerView getFlexRecyclerView() {
        return mFlexRecyclerView;
    }

    @Override
    public void setFlexRecyclerView(FlexRecyclerView flexRecyclerView) {
        mFlexRecyclerView = flexRecyclerView;
    }

    @Override
    public RecyclerView.LayoutManager getRealLayoutManager() {
        return this;
    }

    @Override
    public void setScrollPage(boolean scrollPage) {
        if (scrollPage != mScrollPage) {
            mScrollPage = scrollPage;

            mMaxHeight = 0;
            mItemCount = 0;
            mIndex = Integer.MAX_VALUE;
            mHeight = 0;

            preMeasureHeight();

            // remeasure when scrollpage attribute changed.
            mFlexRecyclerView.resumeRequestLayout();
            mFlexRecyclerView.requestLayout();
        }
    }

    @Override
    public int getOverScrolledY() {
        return mOverScrolledY;
    }

    @Override
    public int getFlexOrientation() {
        return getOrientation();
    }

    @Override
    public void setFlexOrientation(int orientation) {
        setOrientation(orientation);
    }

    @Override
    public void setFlexReverseLayout(boolean reverseLayout) {
        setReverseLayout(reverseLayout);
    }

    @Override
    public boolean canFlexScrollHorizontally() {
        return canScrollHorizontally();
    }

    @Override
    public boolean canFlexScrollVertically() {
        return canScrollVertically();
    }

    @Override
    public int getFlexSpanCount() {
        return getSpanCount();
    }

    @Override
    public void setFlexSpanCount(int spanCount) {
        setSpanCount(spanCount);
    }

    @Override
    public int findFlexFirstVisibleItemPosition() {
        return findFirstVisibleItemPosition();
    }

    @Override
    public int findFlexLastVisibleItemPosition() {
        return findLastVisibleItemPosition();
    }

    @Override
    public int findFlexFirstCompletelyVisibleItemPosition() {
        return findFirstCompletelyVisibleItemPosition();
    }

    @Override
    public int findFlexLastCompletelyVisibleItemPosition() {
        return findLastCompletelyVisibleItemPosition();
    }

    @Override
    public int getFlexItemCount() {
        return getItemCount();
    }

    @Override
    public int getStateItemCount() {
        if (mFlexRecyclerView != null) {
            return mFlexRecyclerView.getState().getItemCount();
        }
        return 0;
    }

    @Override
    public void scrollToFlexPositionWithOffset(int position, int offset) {
        scrollToPositionWithOffset(position, offset);
    }

    @Override
    public View getFlexChildAt(int position) {
        return getChildAt(position);
    }

    @Override
    public int getFlexChildPosition(View view) {
        return getPosition(view);
    }

    @Override
    public boolean onRequestChildFocus(
            @NonNull RecyclerView parent,
            @NonNull RecyclerView.State state,
            @NonNull View child,
            @Nullable View focused) {
        // if focused view is removed from ViewTree,and the result of {super.onResultChildFocus} is
        // false,
        // it will crash in {@linkRecyclerView#requestChildOnScreen}.
        // parameter must be a descendant of this view
        boolean ret = super.onRequestChildFocus(parent, state, child, focused);
        if (!ret && focused != null && !isViewAttached(focused)) {
            ret = true;
        }
        return ret;
    }

    private boolean isViewAttached(View view) {
        if (view == null) {
            return false;
        }

        if (!view.isAttachedToWindow()) {
            return false;
        }

        // if the view was removed from its parent,it's mParent will be null.
        // But if the view is performing animation,when it is removed from it's parent,
        // the mAttachInfo of the view will not be set to null util the animation is complete
        ViewParent parent = view.getParent();
        while (parent instanceof View) {
            parent = parent.getParent();
        }
        // parent == null,the view is dettach from window
        // parent == ViewRootImpl, the top parent is ViewRootImpl
        return parent != null;
    }
}
