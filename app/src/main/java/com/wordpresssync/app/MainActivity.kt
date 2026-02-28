package com.wordpresssync.app

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.wordpresssync.app.api.model.WpPost
import com.wordpresssync.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: com.wordpresssync.app.data.SettingsRepository
    private lateinit var repository: com.wordpresssync.app.data.WordPressRepository
    private lateinit var adapter: PostsAdapter
    private lateinit var catalogAdapter: CatalogAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = com.wordpresssync.app.data.SettingsRepository(this)
        repository = com.wordpresssync.app.data.WordPressRepository(settings)

        binding.editSiteUrl.setText(settings.siteBaseUrl ?: "")
        binding.editUsername.setText(settings.username ?: "")
        binding.editAppPassword.setText(settings.appPassword ?: "")

        adapter = PostsAdapter(emptyList())
        catalogAdapter = CatalogAdapter(emptyList())
        binding.recyclerPosts.layoutManager = LinearLayoutManager(this)
        binding.recyclerPosts.adapter = adapter

        binding.btnSaveUrl.setOnClickListener { saveUrl() }
        binding.btnLoadPosts.setOnClickListener { loadPosts() }
        binding.btnMyCatalog.setOnClickListener { loadCatalog() }
        binding.btnStats.setOnClickListener { loadStats() }
    }

    private fun saveUrl() {
        val url = binding.editSiteUrl.text?.toString()?.trim()
        if (url.isNullOrBlank()) {
            Toast.makeText(this, "Введите URL сайта", Toast.LENGTH_SHORT).show()
            return
        }
        val normalized = if (url.startsWith("http")) url else "https://$url"
        settings.siteBaseUrl = normalized
        settings.username = binding.editUsername.text?.toString()?.trim()
        settings.appPassword = binding.editAppPassword.text?.toString()?.trim()
        repository.clearApiCache()
        Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show()
    }

    private fun loadPosts() {
        if (settings.siteBaseUrl.isNullOrBlank()) {
            binding.textError.visibility = View.VISIBLE
            binding.textError.text = getString(R.string.error_no_url)
            binding.recyclerPosts.visibility = View.GONE
            binding.textEmpty.visibility = View.GONE
            return
        }
        binding.textError.visibility = View.GONE
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerPosts.visibility = View.GONE
        binding.textEmpty.visibility = View.GONE

        lifecycleScope.launch {
            val result = repository.getPosts(perPage = 20, page = 1)
            binding.progressBar.visibility = View.GONE

            result.fold(
                onSuccess = { posts ->
                    if (posts.isEmpty()) {
                        binding.textEmpty.visibility = View.VISIBLE
                        binding.textEmpty.text = "Постов пока нет"
                    } else {
                        binding.recyclerPosts.adapter = adapter
                        adapter.updatePosts(posts)
                        binding.recyclerPosts.visibility = View.VISIBLE
                    }
                },
                onFailure = { e ->
                    binding.textError.visibility = View.VISIBLE
                    binding.textError.text = "${getString(R.string.error_load)}: ${e.message}"
                }
            )
        }
    }

    private fun loadCatalog() {
        if (!settings.hasAuth()) {
            Toast.makeText(this, getString(R.string.auth_required), Toast.LENGTH_LONG).show()
            return
        }
        binding.textError.visibility = View.GONE
        binding.progressBar.visibility = View.VISIBLE
        binding.recyclerPosts.visibility = View.GONE
        binding.textEmpty.visibility = View.GONE

        lifecycleScope.launch {
            val result = repository.getCatalog()
            binding.progressBar.visibility = View.GONE

            result.fold(
                onSuccess = { response ->
                    val items = response.items
                    if (items.isEmpty()) {
                        binding.textEmpty.visibility = View.VISIBLE
                        binding.textEmpty.text = "В каталоге пока нет релизов"
                    } else {
                        binding.recyclerPosts.adapter = catalogAdapter
                        catalogAdapter.updateItems(items)
                        binding.recyclerPosts.visibility = View.VISIBLE
                    }
                },
                onFailure = { e ->
                    binding.textError.visibility = View.VISIBLE
                    binding.textError.text = "${getString(R.string.error_load)}: ${e.message}"
                }
            )
        }
    }

    private fun loadStats() {
        if (!settings.hasAuth()) {
            Toast.makeText(this, getString(R.string.auth_required), Toast.LENGTH_LONG).show()
            return
        }
        binding.progressBar.visibility = View.VISIBLE

        lifecycleScope.launch {
            val result = repository.getStats()
            binding.progressBar.visibility = View.GONE

            result.fold(
                onSuccess = { data ->
                    val sb = StringBuilder()
                    sb.append(getString(R.string.total_plays)).append(": ").append(data.totalPlays).append("\n\n")
                    sb.append(getString(R.string.platforms)).append(":\n")
                    data.platforms?.forEach { (name, count) ->
                        sb.append("  • ").append(name).append(": ").append(count).append("\n")
                    } ?: sb.append("  —\n")
                    AlertDialog.Builder(this@MainActivity)
                        .setTitle(getString(R.string.btn_stats))
                        .setMessage(sb.toString())
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                },
                onFailure = { e ->
                    Toast.makeText(this@MainActivity, "${getString(R.string.error_load)}: ${e.message}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }
}
