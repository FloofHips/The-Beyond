package com.thebeyond.compat.exposure;

import io.github.mortuusars.exposure.world.item.AlbumItem;
import io.github.mortuusars.exposure.world.item.SignedAlbumItem;
import io.github.mortuusars.exposure.world.item.StackedPhotographsItem;
import io.github.mortuusars.exposure.world.item.component.album.AlbumPage;
import io.github.mortuusars.exposure.world.item.component.album.SignedAlbumPage;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.stream.Stream;

final class ExposurePhotos {
    private ExposurePhotos() {
    }

    static List<ItemStack> slides(ItemStack stack) {
        if (stack.getItem() instanceof StackedPhotographsItem stacked) {
            return orItself(stacked.getPhotographs(stack).photographs().stream(), stack);
        }
        if (stack.getItem() instanceof AlbumItem album) {
            return orItself(album.getContent(stack).pages().stream().map(AlbumPage::photograph), stack);
        }
        if (stack.getItem() instanceof SignedAlbumItem album) {
            return orItself(album.getContent(stack).pages().stream().map(SignedAlbumPage::photograph), stack);
        }
        return List.of(stack);
    }

    private static List<ItemStack> orItself(Stream<ItemStack> photographs, ItemStack holder) {
        List<ItemStack> list = photographs.filter(p -> !p.isEmpty()).toList();
        return list.isEmpty() ? List.of(holder) : list;
    }
}
