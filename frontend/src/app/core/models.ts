export interface PricingTier {
  name: string;
  detail: string;
  price: string;
  /** "What's included" bullets, shown behind a toggle on the pricing page. */
  includes: string[];
}

export interface SessionType {
  key: string;
  name: string;
  /** Short label for filter chips and captions. */
  shortName: string;
  description: string;
  from: string;
  /** Portfolio page: show a filter chip and three photo slots for this category. */
  showInPortfolio: boolean;
  tiers: PricingTier[];
}

export interface SessionTypeCreate {
  name: string;
  shortName: string;
  description: string;
  from: string;
  showInPortfolio: boolean;
}

export type BookingStatus = 'PENDING' | 'CONFIRMED' | 'DECLINED';

export interface Booking {
  id: number;
  sessionType: string;
  sessionTypeName: string;
  name: string;
  email: string;
  phone: string | null;
  location: string | null;
  notes: string | null;
  /** ISO date, yyyy-mm-dd */
  date: string;
  time: string;
  status: BookingStatus;
  quote: number | null;
  createdAt: string;
}

export interface BookingRequest {
  sessionType: string;
  name: string;
  email: string;
  phone: string;
  location: string;
  notes: string;
  date: string;
  time: string;
  /** Honeypot — must stay empty. */
  website: string;
}

export interface PublicPopupEvent {
  id: number;
  date: string;
  title: string;
  theme: string;
  venue: string;
  time: string;
}

export interface PopupEvent extends PublicPopupEvent {
  prints: number;
  printPrice: number;
}

export interface PopupEventUpsert {
  date: string;
  title: string;
  theme: string;
  venue: string;
  time: string;
}

export interface Gallery {
  id: number;
  bookingId: number;
  stage: number;
}

export interface PhotoSlotView {
  id: string;
  label: string;
  url: string | null;
}

export interface PhotoGroup {
  title: string;
  slots: PhotoSlotView[];
}

export type EmailAction = 'approve' | 'decline' | 'cancel' | 'message';

/** Draft vs. live: which kinds of site content have unpublished changes. */
export interface PublishStatus {
  dirty: boolean;
  changes: { pricing: boolean; events: boolean; photos: boolean };
  publishedAt: string | null;
}

export interface EmailDraft {
  subject: string;
  body: string;
  to: string;
}

export interface EmailRequest {
  sendEmail?: boolean;
  subject?: string;
  body?: string;
}

export interface AdminUser {
  name: string;
  role: string;
  initial: string;
}

export interface LoginResponse {
  token: string;
  expiresInSeconds: number;
  user: AdminUser;
}
