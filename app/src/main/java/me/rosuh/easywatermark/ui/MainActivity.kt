package me.rosuh.easywatermark.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Observer
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import me.rosuh.easywatermark.R
import me.rosuh.easywatermark.model.WaterMarkConfig
import me.rosuh.easywatermark.ui.about.AboutActivity
import me.rosuh.easywatermark.ui.panel.LayoutFragment
import me.rosuh.easywatermark.ui.panel.StyleFragment
import me.rosuh.easywatermark.ui.panel.TextFragment
import kotlin.math.abs


class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val scope = MainScope()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.my_toolbar))
        scope.launch {
            initView()
            initObserver()
            cl_root.setTransition(R.id.transition_launch)
            cl_root.transitionToEnd()
        }
    }

    private fun initObserver() {
        viewModel.config.observe(this, Observer<WaterMarkConfig> {
            if (it.uri.toString().isEmpty()) {
                return@Observer
            }
            try {
                cl_root.setTransition(R.id.transition_open_image)
                cl_root.transitionToEnd()
                takePersistableUriPermission(it.uri)
                iv_photo?.config = it
            } catch (se: SecurityException) {
                // reset the uri because we don't have permission -_-
                viewModel.updateUri(Uri.parse(""))
            }
        })

        viewModel.saveState.observe(this, Observer { state ->
            when (state) {
                MainViewModel.State.Saving -> {
                    Toast.makeText(this, getString(R.string.tips_saving), Toast.LENGTH_SHORT).show()
                }
                MainViewModel.State.SaveOk -> {
                    Toast.makeText(this, getString(R.string.tips_save_ok), Toast.LENGTH_SHORT)
                        .show()
                }
                MainViewModel.State.ShareOk -> {
                    Toast.makeText(this, getString(R.string.tips_share_ok), Toast.LENGTH_SHORT)
                        .show()
                }
                MainViewModel.State.Error -> {
                    Toast.makeText(
                        this,
                        "${getString(R.string.tips_error)}: ${state.msg}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            if (state == MainViewModel.State.Saving) {
                // todo show loading
            } else {
                // todo hide loading
            }
        })
    }

    private fun initView() {
        my_toolbar.apply {
            navigationIcon =
                ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_logo_tool_bar)
            title = null
        }
        iv_photo.apply {

            setOnTouchListener(object : View.OnTouchListener {
                private var startX = 0f
                private var startY = 0f
                private val verticalFac = 50
                private val horizonFac = 50
                private val leftArea = 0f..(iv_photo.width / 2).toFloat()
                private val rightArea = (iv_photo.width / 2).toFloat()..(iv_photo.width.toFloat())

                override fun onTouch(v: View?, event: MotionEvent?): Boolean {
                    when (event?.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            startX = event.x
                            startY = event.y
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val dx = event.x - startX
                            val dy = event.y - startY
                            if (abs(dx) > verticalFac) {
                                return false
                            }
                            when {
                                (event.x in leftArea) -> {
                                    viewModel.updateAlphaBy(dy / 2)
                                }
                                (event.x in rightArea) -> {
                                    viewModel.updateTextSizeBy(dy / 5)
                                }
                            }
                            startX = event.x
                            startY = event.y
                        }
                    }
                    return true
                }

            })
        }
        iv_picker_tips.setOnClickListener {
            performFileSearch(READ_REQUEST_CODE)
        }

        val titleArray = arrayOf(
            getString(R.string.title_layout),
            getString(R.string.title_style),
            getString(R.string.title_text)
        )

        val iconArray = arrayOf(
            R.drawable.ic_layout_title,
            R.drawable.ic_style_title,
            R.drawable.ic_text_title
        )

        val fragmentArray = arrayOf(
            initFragments(vp_control_panel, 0, LayoutFragment.newInstance()),
            initFragments(vp_control_panel, 1, StyleFragment.newInstance()),
            initFragments(vp_control_panel, 2, TextFragment.newInstance())
        )


        val pagerAdapter = ControlPanelPagerAdapter(this, fragmentArray)
        vp_control_panel.apply {
            offscreenPageLimit = 2
            adapter = pagerAdapter
        }
        TabLayoutMediator(tb_tool_bar, vp_control_panel) { tab, position ->
            when (position) {
                0 -> {
                    tab.text = getString(R.string.title_layout)
                    tab.icon = getDrawable(R.drawable.ic_layout_title)
                }
                1 -> {
                    tab.text = getString(R.string.title_style)
                    tab.icon = getDrawable(R.drawable.ic_style_title)
                }
                2 -> {
                    tab.text = getString(R.string.title_text)
                    tab.icon = getDrawable(R.drawable.ic_text_title)
                }
            }
        }.attach()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
        R.id.action_settings -> {
            startActivity(Intent(this, AboutActivity::class.java))
            true
        }

        R.id.action_pick -> {
            if (isPermissionGrated()) {
                performFileSearch(READ_REQUEST_CODE)
            } else {
                requestPermission()
            }
            true
        }

        R.id.action_save -> {
            if (isPermissionGrated()) {
                viewModel.saveImage(this)
            } else {
                requestPermission()
            }
            true
        }

        R.id.action_share -> {
            if (isPermissionGrated()) {
                viewModel.shareImage(this)
            } else {
                requestPermission()
            }
            true
        }
        else -> {
            super.onOptionsItemSelected(item)
        }
    }

    /**
     * Fires an intent to spin up the "file chooser" UI and select an image.
     */
    private fun performFileSearch(requestCode: Int) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            flags = (Intent.FLAG_GRANT_READ_URI_PERMISSION
                    or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(
            intent,
            requestCode
        )
    }

    private fun isPermissionGrated() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q || ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * 申请权限
     */
    private fun requestPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            WRITE_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            WRITE_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(
                        this,
                        getString(R.string.request_permission_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            READ_REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    data?.data?.also { uri ->
                        viewModel.updateUri(uri)
                        takePersistableUriPermission(uri)
                    }
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.tips_do_not_choose_image),
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }
            }
            ICON_REQUEST_CODE -> {
                if (resultCode == Activity.RESULT_OK) {
                    data?.data?.also { uri ->
                        viewModel.updateIcon(this, uri)
                        takePersistableUriPermission(uri)
                    }
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.tips_do_not_choose_image),
                        Toast.LENGTH_SHORT
                    )
                        .show()
                }
            }
        }
    }

    /**
     * Try to get the permission without timeout.
     */
    private fun takePersistableUriPermission(uri: Uri) {
        val takeFlags: Int = intent.flags and
                (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        contentResolver.takePersistableUriPermission(uri, takeFlags)
    }

    private inner class ControlPanelPagerAdapter(
        fa: FragmentActivity,
        var fragmentArray: Array<Fragment>
    ) : FragmentStateAdapter(fa) {
        override fun getItemCount(): Int = fragmentArray.size

        override fun createFragment(position: Int): Fragment = fragmentArray[position]
    }

    private fun initFragments(vp: ViewPager2, pos: Int, defaultFragment: Fragment): Fragment {
        val tag = "android:switcher:" + vp.id + ":" + pos
        return supportFragmentManager.findFragmentByTag(tag) ?: defaultFragment
    }

    companion object {
        private const val READ_REQUEST_CODE: Int = 42
        private const val WRITE_PERMISSION_REQUEST_CODE: Int = 43
        private const val ICON_REQUEST_CODE: Int = 44
    }
}