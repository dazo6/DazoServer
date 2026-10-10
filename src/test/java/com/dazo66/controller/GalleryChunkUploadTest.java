package com.dazo66.controller;
import com.dazo66.security.*;
import com.dazo66.service.ArtistGalleryCache;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class GalleryChunkUploadTest {
 @TempDir Path root;
 ArtistGalleryCache cache;ArtistGalleryController c;byte[] first=new byte[512*1024];
 @BeforeEach void setup(){cache=mock(ArtistGalleryCache.class);c=new ArtistGalleryController(root.toString(),root.resolve("thumbs"),cache);login("owner");Arrays.fill(first,(byte)3);}
 void login(String id){CurrentUser.set(new CurrentUser(id,id,false,Collections.singletonList("update:*"),null));}
 @AfterEach void clear(){CurrentUser.clear();}
 MockMultipartFile part(byte[] bytes){return new MockMultipartFile("file","a.bin","application/octet-stream",bytes);}
 String start() throws Exception {return (String)c.uploadGalleryChunk(root.toString(),part(first),"a.bin",first.length+3,0,2,null).getBody().getData().get("uploadId");}
 @Test void mergesChunksAndRefreshesIndexOnlyAfterFinalPart() throws Exception {
  String id=start();assertThat(Files.exists(root.resolve("a.bin"))).isFalse();verify(cache,never()).rebuildAuthorized(anyString());
  assertThat(c.uploadGalleryChunk(root.toString(),part(new byte[]{4,5,6}),"a.bin",first.length+3,1,2,id).getBody().getData()).containsEntry("completed",true);
  byte[] data=Files.readAllBytes(root.resolve("a.bin"));assertThat(data.length).isEqualTo(first.length+3);assertThat(data[0]).isEqualTo((byte)3);assertThat(data[data.length-1]).isEqualTo((byte)6);verify(cache,times(1)).rebuildAuthorized(root.toString());
 }
 @Test void protectsOwnerOrderAndRealTimePermissions() throws Exception {
  String id=start();login("other");
  assertThatThrownBy(()->c.uploadGalleryChunk(root.toString(),part(new byte[]{4,5,6}),"a.bin",first.length+3,1,2,id)).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getRawStatusCode()).isEqualTo(403));
  login("owner");assertThatThrownBy(()->c.uploadGalleryChunk(root.toString(),part(first),"a.bin",first.length+3,0,2,id)).isInstanceOf(IllegalArgumentException.class);
  CurrentUser.set(new CurrentUser("owner","owner",false,Collections.singletonList("read:*"),null));assertThatThrownBy(()->c.uploadGalleryChunk(root.toString(),part(new byte[]{4,5,6}),"a.bin",first.length+3,1,2,id)).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getRawStatusCode()).isEqualTo(403));assertThat(Files.exists(root.resolve("a.bin"))).isFalse();
 }
 @Test void refusesConcurrentTargetOverwriteAndCleansTemporaryFile() throws Exception {
  String id=start();Files.write(root.resolve("a.bin"),new byte[]{9});
  assertThatThrownBy(()->c.uploadGalleryChunk(root.toString(),part(new byte[]{4,5,6}),"a.bin",first.length+3,1,2,id)).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getRawStatusCode()).isEqualTo(409));assertThat(Files.readAllBytes(root.resolve("a.bin"))).containsExactly((byte)9);
  try(java.util.stream.Stream<Path> files=Files.list(root.resolve("gallery-uploads"))){assertThat(files.count()).isZero();}
 }
 @Test void enforces500MiBBoundaryAndExpiresAbandonedPartials() throws Exception {
  long limit=500L*1024*1024;
  assertThatThrownBy(()->c.uploadGalleryChunk(root.toString(),part(first),"a.bin",limit+1,0,1001,null)).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getRawStatusCode()).isEqualTo(413));
  String id=(String)c.uploadGalleryChunk(root.toString(),part(first),"a.bin",limit,0,1000,null).getBody().getData().get("uploadId");
  Map<?,?> uploads=(Map<?,?>)ReflectionTestUtils.getField(c,"chunkUploads");ReflectionTestUtils.setField(uploads.get(id),"touched",0L);c.cleanAbandonedUploads();assertThat(uploads).isEmpty();
  try(java.util.stream.Stream<Path> files=Files.list(root.resolve("gallery-uploads"))){assertThat(files.count()).isZero();}
 }
}
