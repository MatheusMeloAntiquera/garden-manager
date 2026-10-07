package com.matheusantiquera.gardenmanager.data.species

import androidx.annotation.StringRes
import com.matheusantiquera.gardenmanager.R

data class Species(
    val id: String,
    val scientificName: String,
    val family: String,
    val category: SpeciesCategory?,
    val commonName: String?,
)

/** Categorias do catálogo. Uma categoria que o app ainda não conhece fica nula e não é exibida. */
enum class SpeciesCategory(val apiValue: String, @StringRes val label: Int) {
    Foliage("folhagem", R.string.species_category_foliage),
    Succulent("suculenta", R.string.species_category_succulent),
    Flower("flor", R.string.species_category_flower),
    Tree("arvore", R.string.species_category_tree),
    Herb("erva", R.string.species_category_herb),
    Vegetable("hortalica", R.string.species_category_vegetable),
    Fruit("frutifera", R.string.species_category_fruit),
    Grass("grama", R.string.species_category_grass),
    ;

    companion object {
        fun fromApi(value: String): SpeciesCategory? = entries.firstOrNull { it.apiValue == value }
    }
}

fun SpeciesResponse.toSpecies(): Species = Species(
    id = id,
    scientificName = scientificName,
    family = family,
    category = SpeciesCategory.fromApi(category),
    commonName = commonName,
)
