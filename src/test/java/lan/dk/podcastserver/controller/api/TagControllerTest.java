package lan.dk.podcastserver.controller.api;

import lan.dk.podcastserver.business.TagBusiness;
import lan.dk.podcastserver.entity.Tag;
import lan.dk.podcastserver.entity.TagAssert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

/**
 * Created by kevin on 15/09/15 for Podcast Server
 */
@RunWith(MockitoJUnitRunner.class)
public class TagControllerTest {

    @Mock TagBusiness tagBusiness;
    @InjectMocks TagController tagController;

    @Test
    public void should_find_tag_by_id() {
        /* Given */
        Integer id = 1;
        Tag value = new Tag();
        when(tagBusiness.findOne(eq(id))).thenReturn(value);

        /* When */
        Tag tagById = tagController.findById(id);

        /* Then */
        TagAssert
                .assertThat(tagById)
                .isSameAs(value);
        verify(tagBusiness, only()).findOne(eq(id));
    }

    @Test
    public void should_find_all_tag() {
        /* Given */
        List<Tag> tags = new ArrayList<>();
        when(tagBusiness.findAll()).thenReturn(tags);

        /* When */
        List<Tag> tagList = tagController.findAll();

        /* Then */
        assertThat(tagList).isSameAs(tags);
    }

}