package com.lagradost.shiro.ui.home

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.lagradost.shiro.ui.result.ShiroResultFragment
import com.lagradost.shiro.*
import com.lagradost.shiro.ShiroApi.Companion.getFullUrlCdn
import com.lagradost.shiro.MainActivity.Companion.activity
import com.lagradost.shiro.ui.GlideApp
import kotlinx.android.synthetic.main.home_card.view.*


class CardAdapter(
    context: Context,
    animeList: ArrayList<ShiroApi.AnimePageData?>,
    resView: RecyclerView
) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var cardList = animeList
    var context: Context? = context
    var resView: RecyclerView? = resView

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return CardViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.home_card, parent, false),
            context!!,
            resView!!
        )
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is CardViewHolder -> {
                holder.bind(cardList[position])
            }

        }
    }

    override fun getItemCount(): Int {
        return cardList.size
    }

    class CardViewHolder
    constructor(itemView: View, _context: Context, resView: RecyclerView) : RecyclerView.ViewHolder(itemView) {
        val context = _context
        val card: ImageView = itemView.imageView
        fun bind(cardInfo: ShiroApi.AnimePageData?) {
            if (cardInfo != null) {
                val glideUrl =
                    GlideUrl(getFullUrlCdn(cardInfo.image))
                //  activity?.runOnUiThread {
                context.let {
                    GlideApp.with(it)
                        .load(glideUrl)
                        .transition(DrawableTransitionOptions.withCrossFade(100))
                        .into(card.imageView)
                }
                itemView.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).start()
                itemView.imageText.text =
                    if (cardInfo.name.endsWith("Dubbed")) "✦ ${
                        cardInfo.name.substring(
                            0,
                            cardInfo.name.length - 6
                        )
                    }" else cardInfo.name

                itemView.home_card_root.setOnLongClickListener {
                    Toast.makeText(context, cardInfo.name, Toast.LENGTH_SHORT).show()
                    return@setOnLongClickListener true
                }
                itemView.home_card_root.setOnClickListener {
                    activity?.supportFragmentManager?.beginTransaction()
                        ?.setCustomAnimations(R.anim.enter, R.anim.exit, R.anim.pop_enter, R.anim.pop_exit)
                        ?.add(R.id.homeRoot, ShiroResultFragment.newInstance(cardInfo))
                        ?.commit()

                }
            }
        }

    }
}
