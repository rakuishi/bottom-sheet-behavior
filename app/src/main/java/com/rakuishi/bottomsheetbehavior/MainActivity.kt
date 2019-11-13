package com.rakuishi.bottomsheetbehavior

import android.annotation.SuppressLint
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.view_bottom_sheet.*

class MainActivity : AppCompatActivity() {

    private val collapsedHeight = dpToPx(32)
    private val bottomSheetBehavior: MyBottomSheetBehavior<View> =
        MyBottomSheetBehavior(collapsedHeight)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Setup BottomSheetBehavior
        val params: CoordinatorLayout.LayoutParams =
            bottomSheet.layoutParams as CoordinatorLayout.LayoutParams
        params.behavior = bottomSheetBehavior
        setupBottomSheetExpandedHeight()

        // OnClickListeners
        halfExpandedButton.setOnClickListener { setState(MyBottomSheetBehavior.STATE_HALF_EXPANDED) }
        expandedButton.setOnClickListener { setState(MyBottomSheetBehavior.STATE_EXPANDED) }
        collapsedButton.setOnClickListener { setState(MyBottomSheetBehavior.STATE_COLLAPSED) }
    }

    private fun setState(@MyBottomSheetBehavior.Companion.State state: Int) {
        bottomSheetBehavior.state = state
    }

    private fun setupBottomSheetExpandedHeight() {
        bottomSheet.viewTreeObserver
            .addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                @SuppressLint("ObsoleteSdkInt")
                override fun onGlobalLayout() {
                    bottomSheetBehavior.halfExpandedHeight =
                        halfExpandedLayout.height + collapsedHeight
                    bottomSheetBehavior.expandedHeight =
                        expandedLayout.height + halfExpandedLayout.height + collapsedHeight

                    // remove the listener:
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                        bottomSheet.viewTreeObserver.removeGlobalOnLayoutListener(this)
                    } else {
                        bottomSheet.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    }
                }
            })
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * Resources.getSystem().displayMetrics.density).toInt()
    }
}
