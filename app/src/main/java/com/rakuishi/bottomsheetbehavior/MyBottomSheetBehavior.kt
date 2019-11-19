package com.rakuishi.bottomsheetbehavior

import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import androidx.annotation.IntDef
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.customview.widget.ViewDragHelper
import java.lang.ref.WeakReference
import kotlin.math.abs

// This code is based on https://github.com/google/iosched/blob/master/mobile/src/main/java/com/google/samples/apps/iosched/widget/BottomSheetBehavior.kt
// But original code has a bug around CoordinatorLayout's height
// ViewDragHelper.Callback, onInterceptTouchEvent and onTouchEvent are changed by using following behavior
// https://github.com/material-components/material-components-android/blob/master/lib/java/com/google/android/material/bottomsheet/BottomSheetBehavior.java
class MyBottomSheetBehavior<V : View>(var collapsedHeight: Int) : CoordinatorLayout.Behavior<V>() {

    companion object {
        /** The bottom sheet is dragging. */
        const val STATE_DRAGGING = 1
        /** The bottom sheet is settling. */
        const val STATE_SETTLING = 2
        /** The bottom sheet is expanded. */
        const val STATE_EXPANDED = 3
        /** The bottom sheet is half-expanded. */
        const val STATE_HALF_EXPANDED = 4
        /** The bottom sheet is collapsed. */
        const val STATE_COLLAPSED = 5

        @IntDef(
            value = [STATE_DRAGGING,
                STATE_SETTLING,
                STATE_EXPANDED,
                STATE_HALF_EXPANDED,
                STATE_COLLAPSED]
        )
        @Retention(AnnotationRetention.SOURCE)
        annotation class State
    }

    private var _state = STATE_COLLAPSED
    @State
    var state
        get() = _state
        set(@State value) {
            if (_state == value) {
                return
            }
            if (viewRef == null) {
                // Child is not laid out yet. Set our state and let onLayoutChild() handle it later.
                if (value == STATE_COLLAPSED ||
                    value == STATE_EXPANDED ||
                    value == STATE_HALF_EXPANDED
                ) {
                    _state = value
                }
                return
            }

            viewRef?.get()?.apply {
                // Start the animation; wait until a pending layout if there is one.
                if (parent != null && parent.isLayoutRequested && isAttachedToWindow) {
                    post {
                        startSettlingAnimation(this, value)
                    }
                } else {
                    startSettlingAnimation(this, value)
                }
            }
        }

    private fun setStateInternal(@State state: Int) {
        if (_state != state) {
            _state = state
        }
    }

    /** Keeps reference to the bottom sheet outside of Behavior callbacks */
    private var viewRef: WeakReference<View>? = null
    /** Controls movement of the bottom sheet */
    private lateinit var dragHelper: ViewDragHelper

    // Touch event handling, etc
    private var initialTouchY = 0
    private var parentHeight = 0
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var acceptTouches = true
    private var velocityTracker: VelocityTracker? = null

    // heights are changed by external
    var halfExpandedHeight = 0
    var expandedHeight = 0

    init {
        setStateInternal(STATE_COLLAPSED)
    }

    // region Layout

    override fun onLayoutChild(parent: CoordinatorLayout, child: V, layoutDirection: Int): Boolean {
        parent.onLayoutChild(child, layoutDirection)

        val savedTop = child.top
        parentHeight = parent.height

        // Offset the bottom sheet
        when (state) {
            STATE_EXPANDED -> ViewCompat.offsetTopAndBottom(child, parentHeight - expandedHeight)
            STATE_HALF_EXPANDED -> ViewCompat.offsetTopAndBottom(
                child,
                parentHeight - halfExpandedHeight
            )
            STATE_COLLAPSED -> ViewCompat.offsetTopAndBottom(child, parentHeight - collapsedHeight)
            STATE_DRAGGING, STATE_SETTLING -> ViewCompat.offsetTopAndBottom(
                child,
                savedTop - child.top
            )
        }

        viewRef = WeakReference(child)
        if (!::dragHelper.isInitialized) {
            dragHelper = ViewDragHelper.create(parent, dragCallback)
        }
        return true
    }

    // endregion

    // region Drag

    private val dragCallback: ViewDragHelper.Callback = object : ViewDragHelper.Callback() {

        override fun tryCaptureView(child: View, pointerId: Int): Boolean {
            when {
                // Sanity check
                state == STATE_DRAGGING -> return false
                // recapture a settling sheet
                dragHelper.viewDragState == ViewDragHelper.STATE_SETTLING -> return true
            }

            return viewRef != null && viewRef?.get() == child
        }

        override fun getViewVerticalDragRange(child: View): Int {
            return parentHeight - collapsedHeight // collapsedOffset
        }

        override fun clampViewPositionVertical(child: View, top: Int, dy: Int): Int {
            val collapsedOffset = parentHeight - collapsedHeight
            val expandedOffset = parentHeight - expandedHeight
            return top.coerceIn(expandedOffset, collapsedOffset)
        }

        override fun clampViewPositionHorizontal(child: View, left: Int, dx: Int) = child.left

        override fun onViewDragStateChanged(state: Int) {
            if (state == ViewDragHelper.STATE_DRAGGING) {
                setStateInternal(STATE_DRAGGING)
            }
        }

        override fun onViewPositionChanged(child: View, left: Int, top: Int, dx: Int, dy: Int) {
            viewRef?.get()?.let { _ ->
                val collapsedOffset = parentHeight - collapsedHeight
                parentHeight - collapsedOffset
            }
        }

        override fun onViewReleased(releasedChild: View, xvel: Float, yvel: Float) {
            settleBottomSheet(releasedChild, yvel)
        }
    }

    private fun settleBottomSheet(sheet: View, yVelocity: Float) {
        @State val targetState: Int
        val collapsedOffset = parentHeight - collapsedHeight
        val halfExpandedOffset = parentHeight - halfExpandedHeight
        val currentTop = sheet.top

        when {
            yVelocity < 0 -> // Moving up
                targetState = if (currentTop > halfExpandedOffset) {
                    STATE_HALF_EXPANDED
                } else {
                    STATE_EXPANDED
                }
            yVelocity > 0 -> // Moving down
                targetState =
                    if (abs(currentTop - halfExpandedOffset) < abs(currentTop - collapsedOffset)) {
                        STATE_HALF_EXPANDED
                    } else {
                        STATE_COLLAPSED
                    }
            else -> targetState = if (currentTop < halfExpandedOffset) {
                if (currentTop < abs(currentTop - collapsedOffset)) {
                    STATE_EXPANDED
                } else {
                    STATE_HALF_EXPANDED
                }
            } else {
                if (abs(currentTop - halfExpandedOffset) < abs(currentTop - collapsedOffset)) {
                    STATE_HALF_EXPANDED
                } else {
                    STATE_COLLAPSED
                }
            }
        }

        startSettlingAnimation(sheet, targetState)
    }

    // endregion

    // region Animation

    private fun startSettlingAnimation(child: View, state: Int) {
        val top: Int
        val finalState = state
        val collapsedOffset = parentHeight - collapsedHeight
        val expandedOffset = parentHeight - expandedHeight
        val halfExpandedOffset = parentHeight - halfExpandedHeight

        top = when (state) {
            STATE_COLLAPSED -> collapsedOffset
            STATE_EXPANDED -> expandedOffset
            STATE_HALF_EXPANDED -> halfExpandedOffset
            else -> throw IllegalArgumentException("Invalid state: $state")
        }

        if (dragHelper.smoothSlideViewTo(child, child.left, top)) {
            setStateInternal(STATE_SETTLING)
            ViewCompat.postOnAnimation(child, SettleRunnable(child, finalState))
        } else {
            setStateInternal(finalState)
        }
    }

    private inner class SettleRunnable(
        private val view: View,
        @State private val state: Int
    ) : Runnable {
        override fun run() {
            if (dragHelper.continueSettling(true)) {
                view.postOnAnimation(this)
            } else {
                setStateInternal(state)
            }
        }
    }

    // endregion

    // region TouchEvent

    override fun onTouchEvent(
        parent: CoordinatorLayout,
        child: V,
        event: MotionEvent
    ): Boolean {
        if (!child.isShown) {
            return false
        }

        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN && state == STATE_DRAGGING) {
            return true
        }

        // CoordinatorLayout can call us before the view is laid out. >_<
        if (::dragHelper.isInitialized) {
            dragHelper.processTouchEvent(event)
        }

        // Record velocity
        if (action == MotionEvent.ACTION_DOWN) {
            resetVelocityTracker()
        }
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(event)

        if (acceptTouches &&
            action == MotionEvent.ACTION_MOVE &&
            exceedsTouchSlop(initialTouchY, event.y.toInt())
        ) {
            // Manually capture the sheet since nothing beneath us is scrolling.
            dragHelper.captureChildView(child, event.getPointerId(event.actionIndex))
        }

        return acceptTouches
    }

    private fun resetVelocityTracker() {
        activePointerId = MotionEvent.INVALID_POINTER_ID
        velocityTracker?.recycle()
        velocityTracker = null
    }

    private fun exceedsTouchSlop(p1: Int, p2: Int) = abs(p1 - p2) >= dragHelper.touchSlop

    override fun onInterceptTouchEvent(
        parent: CoordinatorLayout,
        child: V,
        event: MotionEvent
    ): Boolean {
        if (!child.isShown) {
            acceptTouches = false
            return false
        }

        val action = event.actionMasked

        // Record velocity
        if (action == MotionEvent.ACTION_DOWN) {
            resetVelocityTracker()
        }
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(event)

        when (action) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                if (!acceptTouches) {
                    acceptTouches = true
                    return false
                }
            }

            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(event.actionIndex)
                initialTouchY = event.y.toInt()

                acceptTouches = !(activePointerId == MotionEvent.INVALID_POINTER_ID
                        && !parent.isPointInChildBounds(child, event.x.toInt(), initialTouchY))
            }
        }

        return acceptTouches &&
                // CoordinatorLayout can call us before the view is laid out. >_<
                ::dragHelper.isInitialized &&
                dragHelper.shouldInterceptTouchEvent(event)
    }

    // endregion
}