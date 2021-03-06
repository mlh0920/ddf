/**
 * Copyright (c) Codice Foundation
 * <p>
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package ddf.catalog.transformer;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ddf.catalog.data.Metacard;

public class ThumbnailSupplier
        implements BiFunction<Metacard, Map<String, Serializable>, Optional<BufferedImage>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ThumbnailSupplier.class);

    @Override
    public Optional<BufferedImage> apply(Metacard metacard, Map<String, Serializable> arguments) {
        final byte[] thumbnailBytes = metacard.getThumbnail();

        if (thumbnailBytes != null) {
            try (final InputStream inputStream = new ByteArrayInputStream(thumbnailBytes)) {
                final BufferedImage image = ImageIO.read(inputStream);
                return Optional.ofNullable(image);
            } catch (IOException e) {
                LOGGER.debug("Could not read thumbnail bytes.", e);
            }
        }

        return Optional.empty();
    }
}
