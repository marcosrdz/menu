package com.reactnativemenu

import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.os.Build
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.ForegroundColorSpan
import android.view.*
import android.widget.PopupMenu
import com.facebook.react.bridge.*
import com.facebook.react.uimanager.UIManagerHelper
import com.facebook.react.uimanager.events.RCTEventEmitter
import com.facebook.react.views.view.ReactViewGroup
import java.lang.reflect.Field

class MenuView(private val mContext: ReactContext): ReactViewGroup(mContext) {
  private lateinit var mActions: ReadableArray
  private var mIsAnchoredToRight = false
  private val mPopupMenu: PopupMenu = PopupMenu(context, this)
  private var mIsMenuDisplayed = false
  private var mIsOnLongPress = false
  private var mGestureDetector: GestureDetector

  init {
    mGestureDetector = GestureDetector(mContext, object : GestureDetector.SimpleOnGestureListener() {
      override fun onLongPress(e: MotionEvent) {
        if (!mIsOnLongPress) {
          return
        }
        prepareMenu()
      }

      override fun onSingleTapUp(e: MotionEvent): Boolean {
        if (!mIsOnLongPress) {
          prepareMenu()
        }
        return true
      }
    })
  }

  override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
    return true
  }

  override fun onTouchEvent(ev: MotionEvent): Boolean {
    mGestureDetector.onTouchEvent(ev)
    return true
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    if (mIsMenuDisplayed) {
      mPopupMenu.dismiss()
    }
  }

  fun setActions(actions: ReadableArray) {
    mActions = actions
  }

  fun setIsAnchoredToRight(isAnchoredToRight: Boolean) {
    if (mIsAnchoredToRight == isAnchoredToRight) {
      return
    }
    mIsAnchoredToRight = isAnchoredToRight
  }

  fun setIsOpenOnLongPress(isLongPress: Boolean) {
    mIsOnLongPress = isLongPress
  }

  private val getActionsCount: Int
    get() = mActions.size()

  private fun prepareMenuItem(menuItem: MenuItem, config: ReadableMap?) {
    val titleColor = if (config != null && config.hasKey("titleColor") && !config.isNull("titleColor")) {
      config.getInt("titleColor")
    } else {
      null
    }

    val imageName = if (config != null && config.hasKey("image") && !config.isNull("image")) {
      config.getString("image")
    } else {
      null
    }

    val imageColor = if (config != null && config.hasKey("imageColor") && !config.isNull("imageColor")) {
      config.getInt("imageColor")
    } else {
      null
    }

    val attributes = if (config != null && config.hasKey("attributes") && !config.isNull("attributes")) {
      config.getMap("attributes")
    } else {
      null
    }

    val subactions = if (config != null && config.hasKey("subactions") && !config.isNull("subactions")) {
      config.getArray("subactions")
    } else {
      null
    }

    val menuState = config?.getString("state")

    // Set the title and color
    if (titleColor != null) {
      menuItem.title = getMenuItemTextWithColor(menuItem.title.toString(), titleColor)
    }

    // Set the image and image color
    if (imageName != null) {
      val resourceId: Int = getDrawableIdWithName(imageName)
      if (resourceId != 0) {
        val icon = resources.getDrawable(resourceId, context.theme)
        if (imageColor != null) {
          icon.setTintList(ColorStateList.valueOf(imageColor))
        }
        menuItem.icon = icon
      }
    }

    // Apply attributes like destructive, disabled, hidden
    if (attributes != null) {
      val destructive = if (attributes.hasKey("destructive")) attributes.getBoolean("destructive") else false
      val disabled = if (attributes.hasKey("disabled")) attributes.getBoolean("disabled") else false
      val hidden = if (attributes.hasKey("hidden")) attributes.getBoolean("hidden") else false

      if (destructive) {
        menuItem.title = getMenuItemTextWithColor(menuItem.title.toString(), Color.RED)
      }
      menuItem.isEnabled = !disabled
      menuItem.isVisible = !hidden
    }

    // Set the checkable and checked state
    when (menuState) {
      "on", "off" -> {
        menuItem.isCheckable = true
        menuItem.isChecked = menuState == "on"
      }
      else -> menuItem.isCheckable = false
    }

    // Handle subactions (submenus)
    if (subactions != null && menuItem.hasSubMenu()) {
      for (i in 0 until subactions.size()) {
        val subMenuConfig = subactions.getMap(i)
        val subMenuItem = menuItem.subMenu?.add(Menu.NONE, Menu.NONE, i, subMenuConfig?.getString("title"))
        if (subMenuItem != null) {
          prepareMenuItem(subMenuItem, subMenuConfig)
        }
      }
    }
  }

  private fun prepareMenu() {
    if (getActionsCount > 0) {
      mPopupMenu.menu.clear()
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        mPopupMenu.gravity = if (mIsAnchoredToRight) Gravity.RIGHT else Gravity.LEFT
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        mPopupMenu.setForceShowIcon(true)
      }
      for (i in 0 until getActionsCount) {
        val item = mActions.getMap(i)
        val menuItem = if (item != null && item.hasKey("subactions") && !item.isNull("subactions")) {
          mPopupMenu.menu.addSubMenu(Menu.NONE, Menu.NONE, i, item.getString("title")).item
        } else {
          mPopupMenu.menu.add(Menu.NONE, Menu.NONE, i, item?.getString("title"))
        }
        prepareMenuItem(menuItem, item)
        menuItem.setOnMenuItemClickListener {
          if (!it.hasSubMenu()) {
            mIsMenuDisplayed = false
            if (!mActions.isNull(it.order)) {
              val selectedItem = mActions.getMap(it.order)
              val dispatcher = UIManagerHelper.getEventDispatcherForReactTag(mContext, id)
              val surfaceId: Int = UIManagerHelper.getSurfaceId(this)
              dispatcher?.dispatchEvent(
                MenuOnPressActionEvent(surfaceId, id, selectedItem.getString("id"), id)
              )
            }
            true
          } else {
            false
          }
        }
      }
      mPopupMenu.setOnDismissListener {
        mIsMenuDisplayed = false
      }
      mIsMenuDisplayed = true
      mPopupMenu.show()
    }
  }

  private fun getDrawableIdWithName(name: String): Int {
    val appResources: Resources = context.resources
    var resourceId = appResources.getIdentifier(name, "drawable", context.packageName)
    if (resourceId == 0) {
      resourceId = getResId(name, android.R.drawable::class.java)
    }
    return resourceId
  }

  private fun getResId(resName: String?, c: Class<*>): Int {
    return try {
      val idField: Field = c.getDeclaredField(resName!!)
      idField.getInt(idField)
    } catch (e: Exception) {
      e.printStackTrace()
      0
    }
  }

  private fun getMenuItemTextWithColor(text: String, color: Int): SpannableStringBuilder {
    val textWithColor = SpannableStringBuilder()
    textWithColor.append(text)
    textWithColor.setSpan(ForegroundColorSpan(color),
      0, text.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
    return textWithColor
  }
}
