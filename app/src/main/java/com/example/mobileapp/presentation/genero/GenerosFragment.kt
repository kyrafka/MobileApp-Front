package com.example.mobileapp.presentation.genero

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog as AppAlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.mobileapp.R
import com.example.mobileapp.data.remote.SessionStore
import com.example.mobileapp.data.remote.RetrofitClient
import com.example.mobileapp.data.repository.GeneroRepository
import com.example.mobileapp.presentation.books.GeneroViewModel
import com.example.mobileapp.presentation.books.GeneroViewModelFactory
import com.example.mobileapp.presentation.logreg.LoginFragment
import com.google.android.material.textfield.TextInputEditText
import androidx.core.widget.addTextChangedListener

class GenerosFragment : Fragment(R.layout.fragment_generos) {

    private val viewModel: GeneroViewModel by viewModels {
        GeneroViewModelFactory(GeneroRepository(RetrofitClient.generoApi))
    }

    private lateinit var adapter: GeneroAdapter
    private var generosCache: List<com.example.mobileapp.data.remote.model.genero.GeneroDTO> = emptyList()
    private var filtroGeneroId: Long? = null
    private var filtroEditorial: String? = null
    private var searchQuery: String = ""
    private var librosMapCache: Map<Long, List<com.example.mobileapp.data.remote.model.LibroDTO>> = emptyMap()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Header: mostrar nombre de usuario y acción de logout
        val tvBrand = view.findViewById<TextView>(R.id.tvBrand)
        val ivProfile = view.findViewById<ImageView>(R.id.ivProfile)
        val prefs = requireContext().getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
        val displayName = prefs.getString("USER_NAME", null)
        tvBrand.text = displayName ?: getString(R.string.app_name)

        ivProfile.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Cerrar sesión")
                .setMessage("¿Deseas cerrar sesión?")
                .setPositiveButton("Sí") { _, _ ->
                    // Limpiar preferencias y SessionStore
                    prefs.edit().clear().apply()
                    SessionStore.sessionId = null
                    SessionStore.rol = null
                    // Ir al login
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, com.example.mobileapp.presentation.logreg.LoginFragment())
                        .commit()
                }
                .setNegativeButton("No", null)
                .show()
        }

        // Bottom bar icons
        val navHome = view.findViewById<ImageView>(R.id.navHome)
        val navCart = view.findViewById<ImageView>(R.id.navCart)
        val navOrders = view.findViewById<ImageView>(R.id.navOrders)
        val navAdd = view.findViewById<ImageView>(R.id.navAdd)

        // Recycler + progress + filtros
        val rv = view.findViewById<RecyclerView>(R.id.rvGeneros)
        val progress = view.findViewById<ProgressBar>(R.id.progress)
        val filterContainer = view.findViewById<LinearLayout>(R.id.filterContainer)
        val editorialContainer = view.findViewById<LinearLayout>(R.id.editorialContainer)
        val etSearch = view.findViewById<TextInputEditText>(R.id.etSearch)
        val btnToggleFilters = view.findViewById<ImageButton>(R.id.btnToggleFilters)
        val filterPanel = view.findViewById<LinearLayout>(R.id.filterPanel)

        val sessionId = SessionStore.sessionId ?: ""
        adapter = GeneroAdapter(sessionId) { libro ->
            Toast.makeText(requireContext(), "Click en ${libro.titulo}", Toast.LENGTH_SHORT).show()
            // TODO: abrir detalle del libro cuando lo tengas
        }
        rv.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false)
        rv.adapter = adapter

        // Observers
        viewModel.generos.observe(viewLifecycleOwner) { generos ->
            generosCache = generos
            renderChips(filterContainer, generosCache)
            // cargar libros de cada género
            val sessionIdObs = SessionStore.sessionId ?: ""
            generos.forEach { genero -> genero.idGenero?.let { id -> viewModel.cargarLibrosPorGenero(sessionIdObs, id) } }
        }
        viewModel.librosPorGenero.observe(viewLifecycleOwner) { map ->
            librosMapCache = map
            // construir chips de editoriales desde libros
            renderEditorialChips(editorialContainer, librosMapCache)
            applyFilters()
        }
        viewModel.loading.observe(viewLifecycleOwner) { progress.visibility = if (it) View.VISIBLE else View.GONE }
        viewModel.error.observe(viewLifecycleOwner) {
            if (it != null) Toast.makeText(requireContext(), it, Toast.LENGTH_SHORT).show()
        }

        // Mostrar/ocultar "Agregar" según rol (insensible a mayúsculas/minúsculas y espacios)
        val role = (SessionStore.rol
            ?: requireContext()
                .getSharedPreferences("auth_prefs", Context.MODE_PRIVATE)
                .getString("USER_ROLE", ""))?.trim()
        navAdd.visibility = if ("EMPRESA".equals(role, ignoreCase = true)) View.VISIBLE else View.GONE

        // Clicks bottom bar
        navHome.setOnClickListener {
            // Ya estás en home (géneros). Si usas más pantallas, podrías refrescar.
        }
        navCart.setOnClickListener {
            // TODO: reemplazar por tu CarritoFragment cuando esté
            Toast.makeText(requireContext(), "Ir al Carrito", Toast.LENGTH_SHORT).show()
        }
        navOrders.setOnClickListener {
            // TODO: reemplazar por tu OrdenesFragment cuando esté
            Toast.makeText(requireContext(), "Ir a Órdenes", Toast.LENGTH_SHORT).show()
        }
        navAdd.setOnClickListener {
            // Abrir el formulario para EMPRESA
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, com.example.mobileapp.presentation.libro.AddLibroFragment())
                .addToBackStack(null)
                .commit()
        }

        // Cargar datos
        viewModel.cargarGeneros(SessionStore.sessionId ?: "")

        // Búsqueda de texto
        etSearch.addTextChangedListener { editable ->
            searchQuery = editable?.toString()?.trim().orEmpty()
            applyFilters()
        }

        // Expandir/contraer panel de filtros
        btnToggleFilters.setOnClickListener {
            filterPanel.visibility = if (filterPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
    }

    override fun onResume() {
        super.onResume()
        // Refrescar al volver del formulario
        viewModel.cargarGeneros(SessionStore.sessionId ?: "")
    }

    private fun renderChips(container: LinearLayout, generos: List<com.example.mobileapp.data.remote.model.genero.GeneroDTO>) {
        container.removeAllViews()

        // Chip "Todos"
        container.addView(makeChip(container, "TODOS", isSelected = (filtroGeneroId == null)) {
            filtroGeneroId = null
            adapter.submitGeneros(generos)
            // actualizar selección visual
            renderChips(container, generos)
        })

        // Chips por género
        generos.forEach { g ->
            val selected = (filtroGeneroId == g.idGenero)
            container.addView(makeChip(container, g.nombre, isSelected = selected) {
                filtroGeneroId = if (selected) null else g.idGenero
                renderChips(container, generos)
                applyFilters()
            })
        }
    }

    private fun makeChip(parent: ViewGroup, text: String, isSelected: Boolean, onClick: () -> Unit): TextView {
        return TextView(requireContext()).apply {
            this.text = text
            setPadding(24, 12, 24, 12)
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.marginEnd = 16
            layoutParams = params
            background = resources.getDrawable(if (isSelected) R.drawable.bg_chip_selected else R.drawable.bg_chip_unselected, null)
            setTextColor(resources.getColor(if (isSelected) android.R.color.white else android.R.color.black, null))
            setOnClickListener { onClick() }
        }
    }

    private fun renderEditorialChips(container: LinearLayout, librosMap: Map<Long, List<com.example.mobileapp.data.remote.model.LibroDTO>>) {
        container.removeAllViews()

        val editoriales = librosMap.values.flatten().mapNotNull { it.editorial?.trim() }
            .filter { it.isNotEmpty() }
            .distinct().sorted()

        // Chip "Todas"
        container.addView(makeChip(container, "TODAS", isSelected = (filtroEditorial == null)) {
            filtroEditorial = null
            applyFilters()
        })

        editoriales.forEach { editorial ->
            val selected = (filtroEditorial.equals(editorial, ignoreCase = true))
            container.addView(makeChip(container, editorial, isSelected = selected) {
                filtroEditorial = if (selected) null else editorial
                applyFilters()
            })
        }
    }

    private fun applyFilters() {
        val generosMostrar = filtroGeneroId?.let { id -> generosCache.filter { it.idGenero == id } } ?: generosCache

        adapter.submitGeneros(generosMostrar)

        generosMostrar.forEach { genero ->
            val id = genero.idGenero ?: return@forEach
            val originales = librosMapCache[id].orEmpty()
            val filtrados = originales.filter { libro ->
                val pasaEditorial = filtroEditorial?.let { ed -> (libro.editorial ?: "").equals(ed, ignoreCase = true) } ?: true
                val q = searchQuery
                val pasaBusqueda = if (q.isEmpty()) true else run {
                    val t = libro.titulo ?: ""
                    val a = libro.nombreCompletoAutor ?: ""
                    val s = libro.sinopsis ?: ""
                    t.contains(q, ignoreCase = true) || a.contains(q, ignoreCase = true) || s.contains(q, ignoreCase = true)
                }
                pasaEditorial && pasaBusqueda
            }
            adapter.submitLibros(id, filtrados)
        }
    }
}
