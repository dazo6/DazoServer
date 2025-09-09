package com.dazo66.crawler.kemono.pipeline;

import com.dazo66.crawler.kemono.KemonoJsonFavourite;
import com.dazo66.entity.Artist;
import com.dazo66.entity.ArtistDomainEnum;
import com.dazo66.service.KemonoArtistService;
import com.geccocrawler.gecco.pipeline.Pipeline;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@Slf4j
public class KemonoJsonFavouritePipeline implements Pipeline<KemonoJsonFavourite> {

    @Autowired
    private KemonoArtistService kemonoArtistService;

    @Override
    public void process(KemonoJsonFavourite bean) {

        for (KemonoJsonFavourite.FanboxCreator creator : bean.getArtists()) {
            if (creator == null || kemonoArtistService.getArtistId(creator.getId(), ArtistDomainEnum.KEMONO) != null) {
                continue;
            }
            Artist artist = new Artist();
            artist.setArtistId(creator.getId());
            artist.setEnable(true);
            artist.setName(creator.getName());
            artist.setType(creator.getService());
            artist.setDomain(ArtistDomainEnum.KEMONO);
            artist.setGmtCreate(new Date());
            kemonoArtistService.addArtist(artist);
            log.info("自动订阅作者成功: {},", artist);
        }

    }
}
