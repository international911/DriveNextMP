package com.example.drivenext

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class CarAdapter(private val onDetails: (Car) -> Unit, private val onBook: (Car) -> Unit) : RecyclerView.Adapter<CarAdapter.Holder>() {
    private var items: List<Car> = emptyList()
    fun submit(cars: List<Car>) { items = cars; notifyDataSetChanged() }
    override fun getItemCount(): Int = items.size
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_car, parent, false))
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val car = items[position]
        holder.name.text = "${car.brand} ${car.model}"
        holder.features.text = car.features
        holder.price.text = "${car.price} ₽ / сутки"
        holder.photo.setImageResource(car.photoResource())
        holder.details.setOnClickListener { onDetails(car) }
        holder.book.setOnClickListener { onBook(car) }
    }
    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.carName)
        val features: TextView = view.findViewById(R.id.carFeatures)
        val price: TextView = view.findViewById(R.id.carPrice)
        val photo: ImageView = view.findViewById(R.id.carPhoto)
        val details: Button = view.findViewById(R.id.carDetails)
        val book: Button = view.findViewById(R.id.carBook)
    }
}
