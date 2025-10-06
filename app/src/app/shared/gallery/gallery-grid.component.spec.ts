import { GalleryGridComponent } from './gallery-grid.component';

describe('GalleryGridComponent', () => {
  it('emits photoClick when an image is clicked', () => {
    const comp = new GalleryGridComponent();
    const photos = [{ id: '1', url: 'a', thumbUrl: 'a' }];
    comp.photos = photos as any;

    let emitted = -1;
    comp.photoClick.subscribe((i) => (emitted = i));
    comp.click(0);
    expect(emitted).toBe(0);
  });
});
