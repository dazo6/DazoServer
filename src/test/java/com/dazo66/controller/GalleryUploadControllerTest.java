package com.dazo66.controller;
import com.dazo66.security.*;
import com.dazo66.service.*;
import com.dazo66.dashboard.ArtistGalleryItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.nio.file.*;
import java.io.*;
import java.util.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class GalleryUploadControllerTest {
 @TempDir Path root;
 @BeforeEach void login(){CurrentUser.set(new CurrentUser("admin","admin",true,Arrays.asList("read:*","update:*"),null));}
 @AfterEach void logout(){CurrentUser.clear();}
 byte[] png() throws Exception {ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(8,8,BufferedImage.TYPE_INT_RGB),"png",out);return out.toByteArray();}
 ArtistGalleryController controller(ArtistGalleryCache cache){return new ArtistGalleryController(root.toString(),root.resolve("thumbs"),cache);}
 @Test void writesFileRebuildsIndexAndDoesNotOverwriteExistingFile() throws Exception {
  Path gallery=Files.createDirectory(root.resolve("artist"));
  ArtistGalleryCache cache=spy(new ArtistGalleryCache(root,root.resolve("indexes"),new ObjectMapper()));
  cache.getOrBuild(gallery.toString());ArtistGalleryController c=controller(cache);byte[] bytes=png();
  assertThat(c.uploadGalleryFile(gallery.toString(),new MockMultipartFile("file","photo.png","image/png",bytes)).getStatusCodeValue()).isEqualTo(200);
  assertThat(Files.readAllBytes(gallery.resolve("photo.png"))).isEqualTo(bytes);
  verify(cache,times(1)).rebuildAuthorized(gallery.toString());
  assertThat(c.getGallery(gallery.toString(),"name","asc").getBody().getData().getImages()).extracting(ArtistGalleryItem::getName).containsExactly("photo.png");
  assertThatThrownBy(()->c.uploadGalleryFile(gallery.toString(),new MockMultipartFile("file","photo.png","image/png",new byte[]{7}))).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getRawStatusCode()).isEqualTo(409));
  assertThat(Files.readAllBytes(gallery.resolve("photo.png"))).isEqualTo(bytes);
  try(java.util.stream.Stream<Path> paths=Files.list(gallery)){assertThat(paths.count()).isEqualTo(1);}
 }
 @Test void rejectsReadOnlyTraversalOversizeAndOutOfRoot() throws Exception {
  Path gallery=Files.createDirectory(root.resolve("artist"));ArtistGalleryController c=controller(mock(ArtistGalleryCache.class));
  CurrentUser.set(new CurrentUser("reader","reader",false,Collections.singletonList("read:"+gallery+"/*"),null));
  assertThatThrownBy(()->c.uploadGalleryFile(gallery.toString(),new MockMultipartFile("file","a.png","image/png",new byte[]{1}))).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getRawStatusCode()).isEqualTo(403));
  login();
  for(String name:Arrays.asList("../a.png","..\\a.png","D:/a.png","a.png:stream"))assertThatThrownBy(()->c.uploadGalleryFile(gallery.toString(),new MockMultipartFile("file",name,"image/png",new byte[]{1}))).isInstanceOf(IllegalArgumentException.class);
  MultipartFile large=mock(MultipartFile.class);when(large.getSize()).thenReturn(500L*1024*1024+1);
  assertThatThrownBy(()->c.uploadGalleryFile(gallery.toString(),large)).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getRawStatusCode()).isEqualTo(413));verify(large,never()).getInputStream();
  Path outside=Files.createTempDirectory("gallery-upload-outside-");try{assertThatThrownBy(()->c.uploadGalleryFile(outside.toString(),new MockMultipartFile("file","a.png","image/png",new byte[]{1}))).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getRawStatusCode()).isEqualTo(404));}finally{Files.delete(outside);}
 }
 @Test void accepts500MiBBoundaryAndRegisteredExternalGallery() throws Exception {
  Path external=Files.createTempDirectory("registered-gallery-");
  try{
   FreeGalleryService free=mock(FreeGalleryService.class);when(free.isRegisteredDirectory(external.toRealPath())).thenReturn(true);
   ArtistGalleryCache cache=spy(new ArtistGalleryCache(root,root.resolve("indexes"),new ObjectMapper()));
   ArtistGalleryController c=new ArtistGalleryController(root.toString(),root.resolve("thumbs"),cache,free);
   MultipartFile file=mock(MultipartFile.class);when(file.getOriginalFilename()).thenReturn("boundary.bin");when(file.getSize()).thenReturn(500L*1024*1024);when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1}));
   assertThat(c.uploadGalleryFile(external.toString(),file).getStatusCodeValue()).isEqualTo(200);verify(cache).rebuildAuthorized(external.toString());
  }finally{Files.deleteIfExists(external.resolve("boundary.bin"));Files.delete(external);}
 }
}
